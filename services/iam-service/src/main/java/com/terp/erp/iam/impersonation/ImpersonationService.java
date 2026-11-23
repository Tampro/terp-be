package com.terp.erp.iam.impersonation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.auth.AccountTokens;
import com.terp.erp.iam.constant.EDefaultRole;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.RoleRepository;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Login as Company (docs/analysis/06 §3.2.1, PO 2026-11-21). The super admin keeps their own Keycloak token and
 * opens a session; while the FE sends its key in {@value #HEADER}, every ERP service resolves the caller as that
 * company's administrator (its COMPANY_ADMIN permissions, superAdmin = false, so company isolation applies as for any
 * company user). Records written meanwhile carry the super admin as created_by; every session is kept for audit.
 */
@Slf4j
@Service
public class ImpersonationService {
    public static final String HEADER = com.terp.erp.security.RemoteAuthorityResolver.ACT_AS_HEADER;
    static final Duration VALIDITY = Duration.ofHours(1);

    private final ImpersonationSessionRepository sessions;
    private final CompanyRepository companies;
    private final RoleRepository roles;
    private final Clock clock;
    private final com.terp.erp.iam.saas.PlanModuleFilter planModules;
    // The principal is resolved on every request of the session: a short cache, cleared when a session ends.
    private final Cache<String, Optional<ErpPrincipal>> resolved = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(15)).maximumSize(1_000).build();

    /** Entries held now (Caffeine's estimate), for the super admin Cache page. */
    public long estimatedCacheSize() {
        return resolved.estimatedSize();
    }

    /** Clear Cache: sessions are resolved from the database again on the next request (none is ended). */
    public void clearCache() {
        resolved.invalidateAll();
    }

    public ImpersonationService(ImpersonationSessionRepository sessions, CompanyRepository companies,
                                RoleRepository roles, Clock clock,
                                com.terp.erp.iam.saas.PlanModuleFilter planModules) {
        this.planModules = planModules;
        this.sessions = sessions;
        this.companies = companies;
        this.roles = roles;
        this.clock = clock;
    }

    public record StartedDto(@Schema(description = "Send it as " + HEADER + " on every request; shown once")
                             String sessionKey, String sessionId, String companyId, String companyName,
                             Instant expiresAt) {
    }

    public record SessionDto(String id, String companyId, String superAdminUsername, String clientIp,
                             Instant startedAt, Instant expiresAt, Instant endedAt, boolean active) {
        static SessionDto of(ImpersonationSession s, Instant now) {
            return new SessionDto(s.getId().toString(), s.getCompanyId().toString(), s.getSuperAdminUsername(),
                    s.getClientIp(), s.getCreatedDate(), s.getExpiresAt(), s.getEndedAt(), s.activeAt(now));
        }
    }

    /** Super admin (without the header) opens a session on a company for {@link #VALIDITY}. */
    @Transactional
    public StartedDto start(Long companyId, String clientIp) {
        ErpPrincipal p = CurrentUser.get();
        Company c = companies.findByIdAndDeletedFalse(com.terp.erp.common.Nulls.nonNull(companyId))
                .orElseThrow(() -> ApiException.notFound(EIamError.IAM_001));
        String key = AccountTokens.newToken();
        ImpersonationSession s = new ImpersonationSession();
        s.setKeyHash(AccountTokens.hash(key));
        s.setSuperAdminSub(p.keycloakId());
        s.setSuperAdminUsername(p.username());
        s.setCompanyId(c.getId());
        s.setClientIp(clientIp);
        s.setExpiresAt(clock.instant().plus(VALIDITY));
        s = sessions.save(s);
        log.info("Super admin {} started Login as Company {} (session {})", p.username(), c.getCode(), s.getId());
        return new StartedDto(key, s.getId().toString(), c.getId().toString(), c.getName(), s.getExpiresAt());
    }

    /** Ends the caller's own session (by its key); unknown or another admin's key changes nothing. */
    @Transactional
    public void end(String key, String superAdminSub) {
        sessions.findByKeyHash(AccountTokens.hash(key)).filter(s -> s.getSuperAdminSub().equals(superAdminSub))
                .filter(s -> s.getEndedAt() == null).ifPresent(s -> {
                    s.setEndedAt(clock.instant());
                    log.info("Login as Company session {} ended", s.getId());
                });
        resolved.invalidateAll();
    }

    @Transactional(readOnly = true)
    public Page<SessionDto> history(Long companyId, Pageable pageable) {
        Instant now = clock.instant();
        return sessions.search(companyId, pageable).map(s -> SessionDto.of(s, now));
    }

    /**
     * The caller as the company's administrator, when the key is an active session of this super admin; empty
     * otherwise (the request is then refused). The permissions are the company's COMPANY_ADMIN role's.
     */
    @Transactional(readOnly = true)
    public Optional<ErpPrincipal> principalFor(String key, String superAdminSub, String username) {
        return resolved.get(superAdminSub + "|" + key, k -> load(key, superAdminSub, username));
    }

    private Optional<ErpPrincipal> load(String key, String superAdminSub, String username) {
        Instant now = clock.instant();
        return sessions.findByKeyHash(AccountTokens.hash(key))
                .filter(s -> s.getSuperAdminSub().equals(superAdminSub) && s.activeAt(now))
                .flatMap(s -> roles.findByCompanyIdAndCodeAndDeletedFalse(s.getCompanyId(),
                        EDefaultRole.COMPANY_ADMIN.name()).map(role -> {
                    // Same plan limits as the company's own admin: the super admin sees what the company sees.
                    Company company = companies.findByIdAndDeletedFalse(s.getCompanyId()).orElse(null);
                    Set<String> perms = company == null
                            ? role.getPermissions().stream().map(x -> x.getCode()).collect(Collectors.toUnmodifiableSet())
                            : planModules.allowed(company, role.getPermissions());
                    return new ErpPrincipal(null, superAdminSub, username, s.getCompanyId(), false,
                            Set.of(EDefaultRole.COMPANY_ADMIN.name()), perms);
                }));
    }
}
