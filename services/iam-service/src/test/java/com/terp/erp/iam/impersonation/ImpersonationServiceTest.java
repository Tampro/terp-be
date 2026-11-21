package com.terp.erp.iam.impersonation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.terp.erp.iam.auth.AccountTokens;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Permission;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.RoleRepository;
import com.terp.erp.security.ErpPrincipal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImpersonationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private final ImpersonationSessionRepository sessions = mock(ImpersonationSessionRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final ImpersonationService service = new ImpersonationService(sessions, companies, roles,
            Clock.fixed(NOW, ZoneOffset.UTC), com.terp.erp.iam.saas.PlanModuleFilterTest.noPlan());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void startStoresOnlyTheHashAndTheSessionActsAsTheCompanyAdmin() {
        superAdmin("kc-root");
        Company c = new Company();
        c.setId(10L);
        c.setCode("ACME");
        c.setName("Acme");
        when(companies.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(c));
        when(sessions.save(any())).thenAnswer(i -> {
            ImpersonationSession s = i.getArgument(0);
            s.setId(5L);
            return s;
        });

        var started = service.start(10L, "203.0.113.9");

        ArgumentCaptor<ImpersonationSession> saved = ArgumentCaptor.forClass(ImpersonationSession.class);
        verify(sessions).save(saved.capture());
        ImpersonationSession s = saved.getValue();
        assertThat(s.getKeyHash()).isEqualTo(AccountTokens.hash(started.sessionKey())).isNotEqualTo(started.sessionKey());
        assertThat(s.getExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(s.getSuperAdminSub()).isEqualTo("kc-root");

        Role admin = new Role();
        Permission p = new Permission();
        p.setCode("EMPLOYEE_VIEW");
        admin.getPermissions().add(p);
        when(sessions.findByKeyHash(s.getKeyHash())).thenReturn(Optional.of(s));
        when(roles.findByCompanyIdAndCodeAndDeletedFalse(10L, "COMPANY_ADMIN")).thenReturn(Optional.of(admin));

        ErpPrincipal acting = service.principalFor(started.sessionKey(), "kc-root", "root").orElseThrow();
        assertThat(acting.superAdmin()).isFalse();
        assertThat(acting.companyId()).isEqualTo(10L);
        assertThat(acting.keycloakId()).isEqualTo("kc-root");
        assertThat(acting.permissions()).containsExactly("EMPLOYEE_VIEW");
        // another super admin cannot use this key
        assertThat(service.principalFor(started.sessionKey(), "kc-other", "other")).isEmpty();

        // ended: refused from then on
        service.end(started.sessionKey(), "kc-root");
        assertThat(s.getEndedAt()).isEqualTo(NOW);
        assertThat(service.principalFor(started.sessionKey(), "kc-root", "root")).isEmpty();
    }

    @Test
    void anExpiredSessionIsRefused() {
        ImpersonationSession s = new ImpersonationSession();
        s.setKeyHash(AccountTokens.hash("k"));
        s.setSuperAdminSub("kc-root");
        s.setCompanyId(10L);
        s.setExpiresAt(NOW.minusSeconds(1));
        when(sessions.findByKeyHash(s.getKeyHash())).thenReturn(Optional.of(s));
        assertThat(service.principalFor("k", "kc-root", "root")).isEmpty();
    }

    private static void superAdmin(String sub) {
        ErpPrincipal p = new ErpPrincipal(null, sub, "root", null, true, Set.of(), Set.of());
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("sub", sub));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), sub);
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
