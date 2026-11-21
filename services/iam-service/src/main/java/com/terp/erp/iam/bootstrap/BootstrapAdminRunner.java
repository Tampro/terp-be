package com.terp.erp.iam.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import com.terp.erp.iam.constant.ECompanyStatus;
import com.terp.erp.iam.constant.EDefaultRole;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.keycloak.KeycloakClient;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.RoleRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.role.RoleService;
import com.terp.erp.security.ErpPrincipal;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Idempotent: creates the bootstrap company (with the default roles of {@link EDefaultRole}) if its code is new,
 * and the administrator account if the username is new — in Keycloak with the operator's password (permanent) and
 * in IAM with the COMPANY_ADMIN role. Never changes an existing account or password; never logs the password.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminRunner implements ApplicationRunner {
    private final BootstrapProperties props;
    private final CompanyRepository companies;
    private final RoleRepository roles;
    private final UserAccountRepository users;
    private final RoleService roleService;
    private final KeycloakClient keycloak;
    private final TransactionTemplate tx;

    @Override
    public void run(ApplicationArguments args) {
        if (!props.enabled()) {
            return;
        }
        // Audit columns need a caller; the bootstrap acts as the system, not as a person.
        asSystem(() -> tx.executeWithoutResult(s -> bootstrap()));
    }

    private void bootstrap() {
        Company company = companies.findAll().stream()
                .filter(c -> !c.isDeleted() && c.getCode().equals(props.companyCode()))
                .findFirst()
                .orElseGet(this::createCompany);

        if (users.existsByUsernameAndDeletedFalse(props.adminUsername())) {
            log.info("Bootstrap admin '{}' already exists — left unchanged", props.adminUsername());
            return;
        }
        Role admin = roles.findByCompanyIdAndCodeAndDeletedFalse(company.getId(), EDefaultRole.COMPANY_ADMIN.name())
                .orElseThrow(() -> new IllegalStateException("COMPANY_ADMIN role missing in " + company.getCode()));

        String keycloakId = keycloak.findUserId(props.adminUsername());
        if (keycloakId == null) {
            keycloakId = keycloak.createUser(props.adminUsername(), props.adminEmail(), props.adminFullName(),
                    props.adminPassword(), false);
        }
        UserAccount u = new UserAccount();
        u.setKeycloakId(keycloakId);
        u.setCompanyId(company.getId());
        u.setUsername(props.adminUsername());
        u.setEmail(props.adminEmail());
        u.setFullName(props.adminFullName());
        u.setStatus(EUserStatus.ACTIVE);
        u.setRoles(new HashSet<>(Set.of(admin)));
        users.save(u);
        log.info("Bootstrap admin '{}' created as COMPANY_ADMIN of {}", props.adminUsername(), company.getCode());
    }

    private Company createCompany() {
        Company c = new Company();
        c.setCode(props.companyCode());
        c.setName(props.companyName());
        c.setStatus(ECompanyStatus.ACTIVE);
        c = companies.save(c);
        c.setReferralCode(com.terp.erp.iam.referral.ReferralService.codeFor(c.getId()));
        roleService.createDefaultRoles(c.getId());
        log.info("Bootstrap company {} created with default roles", c.getCode());
        return c;
    }

    private static void asSystem(Runnable action) {
        ErpPrincipal system = new ErpPrincipal(null, "system", "system", null, true, Set.of(), Set.of());
        Jwt jwt = new Jwt("bootstrap", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", "system"));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), "system");
        auth.setDetails(system);
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
