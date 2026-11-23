package com.terp.erp.iam.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;
import com.terp.erp.iam.constant.ECompanyStatus;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Permission;
import com.terp.erp.iam.saas.SaasPermissions;
import com.terp.erp.iam.saas.SubscriptionService;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.security.AuthorityResolver;
import com.terp.erp.security.ErpJwtAuthenticationConverter;
import com.terp.erp.security.ErpPrincipal;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the caller from the IAM database on every request (behind a 30 s cache). A valid Keycloak token is not
 * enough: a locked account or a suspended company is rejected with 401 here, even before the token expires.
 */
@Component
@RequiredArgsConstructor
public class LocalAuthorityResolver implements AuthorityResolver {
    private final UserAccountRepository users;
    private final CompanyRepository companies;
    private final RevokedSessionStore revokedSessions;
    private final SubscriptionService subscriptions;
    private final com.terp.erp.iam.impersonation.ImpersonationService impersonation;
    private final com.terp.erp.iam.saas.PlanModuleFilter planModules;

    private final Cache<String, Optional<ErpPrincipal>> cache =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).maximumSize(10_000).build();

    @Override
    public ErpPrincipal resolve(Jwt jwt) {
        // Checked before the cache: a logged-out session must stop working on the next request.
        if (revokedSessions.isRevoked(jwt.getClaimAsString("sid"))) {
            throw new InvalidBearerTokenException("Session logged out");
        }
        boolean superAdmin = ErpJwtAuthenticationConverter.isSuperAdmin(jwt);
        String actAs = com.terp.erp.security.RemoteAuthorityResolver.actAsHeader();
        if (actAs != null) {
            // Login as Company: only a super admin's own active session; anything else is refused, never ignored.
            if (!superAdmin) {
                throw new InvalidBearerTokenException("Act-as header without super admin");
            }
            return impersonation.principalFor(actAs, jwt.getSubject(), jwt.getClaimAsString("preferred_username"))
                    .orElseThrow(() -> new InvalidBearerTokenException("Act-as session ended or expired"));
        }
        Optional<ErpPrincipal> local = cache.get(jwt.getSubject(), this::load);
        if (local.isPresent()) {
            ErpPrincipal p = local.get();
            return superAdmin ? new ErpPrincipal(p.userId(), p.keycloakId(), p.username(), p.companyId(), true,
                    p.roles(), p.permissions()) : p;
        }
        if (superAdmin) {
            return new ErpPrincipal(null, jwt.getSubject(), jwt.getClaimAsString("preferred_username"), null, true,
                    Set.of(), Set.of());
        }
        // Signed in at Keycloak but no ERP profile: authenticated with no permissions, so GET /me can explain it.
        return new ErpPrincipal(null, jwt.getSubject(), jwt.getClaimAsString("preferred_username"), null, false,
                Set.of(), Set.of());
    }

    /** Called after any change to roles, permissions, user roles or status. */
    public void evictAll() {
        cache.invalidateAll();
    }

    /** Entries held now (Caffeine's estimate), for the super admin Cache page. */
    public long estimatedSize() {
        return cache.estimatedSize();
    }

    // No transaction needed: the entity graph fetches roles and permissions in the one query.
    private Optional<ErpPrincipal> load(String keycloakId) {
        Optional<UserAccount> found = users.findByKeycloakIdAndDeletedFalse(keycloakId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        UserAccount u = found.get();
        Optional<Company> company = companies.findByIdAndDeletedFalse(u.getCompanyId());
        boolean companyActive = company.map(c -> c.getStatus() == ECompanyStatus.ACTIVE).orElse(false);
        if (u.getStatus() != EUserStatus.ACTIVE || !companyActive) {
            throw new InvalidBearerTokenException("Account locked or company inactive");
        }
        Set<Role> roles = u.getRoles().stream().filter(r -> !r.isDeleted()).collect(Collectors.toSet());
        Set<String> roleCodes = roles.stream().map(Role::getCode).collect(Collectors.toUnmodifiableSet());
        // Only the modules of the company's plan: a module left out of the plan answers 403 in every service.
        Set<String> permissions = planModules.allowed(company.get(),
                roles.stream().flatMap(r -> r.getPermissions().stream()).collect(Collectors.toSet()));
        if (subscriptions.effective(company.get()).blocked()) {
            // Expired subscription: only someone who can renew gets in, and only with the renewal permissions.
            if (!SubscriptionService.mayRenew(roleCodes)) {
                throw new InvalidBearerTokenException("Subscription expired");
            }
            permissions = SaasPermissions.ALL;
        }
        return Optional.of(new ErpPrincipal(u.getId(), u.getKeycloakId(), u.getUsername(), u.getCompanyId(), false,
                roleCodes, permissions));
    }
}
