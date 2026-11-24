package com.terp.erp.iam.saas;

import com.terp.erp.iam.constant.EDefaultRole;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.keycloak.KeycloakClient;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Plan downgrade: when the company's plan allows fewer users than it has active, the newest active accounts beyond
 * the limit are locked, as the reference product does (TERP Core, observed 2026-11-24; PO chose it the same day).
 * Company administrators are kept first so the company can still manage its subscription. Nothing is unlocked on an
 * upgrade: an administrator re-activates the accounts they want, within the limit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanSeatEnforcer {
    private final SubscriptionService subscriptions;
    private final UserAccountRepository users;
    private final KeycloakClient keycloak;
    private final LocalAuthorityResolver authorityCache;

    /** Locks the accounts over the plan limit; returns how many were locked. Call after the plan changed. */
    public int enforce(Company company) {
        var e = subscriptions.effective(company);
        if (e.plan() == null || e.plan().getMaxUsers() == null) {
            return 0;
        }
        int max = e.plan().getMaxUsers();
        List<UserAccount> active = users.findByCompanyIdAndDeletedFalse(company.getId()).stream()
                .filter(u -> u.getStatus() == EUserStatus.ACTIVE)
                .sorted(Comparator.comparing((UserAccount u) -> !isAdmin(u))
                        .thenComparing(UserAccount::getCreatedDate, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                        .thenComparing(UserAccount::getId))
                .toList();
        if (active.size() <= max) {
            return 0;
        }
        List<UserAccount> over = active.subList(max, active.size());
        for (UserAccount u : over) {
            u.setStatus(EUserStatus.LOCKED);
            try {
                keycloak.setEnabled(u.getKeycloakId(), false);
            } catch (RuntimeException ex) {
                // The plan change (often a captured payment) must not roll back for this. LOCKED in IAM already
                // denies every permission and login; the Keycloak session simply lives until its token expires.
                log.warn("Plan limit: Keycloak disable failed for user {} of company {}: {}", u.getId(),
                        company.getId(), ex.getClass().getSimpleName());
            }
        }
        authorityCache.evictAll();
        log.info("Plan limit {}: locked {} account(s) of company {}", max, over.size(), company.getId());
        return over.size();
    }

    /**
     * Re-activating an account: locked accounts hold a seat like active ones (PO 2026-11-24, same count as
     * {@link SubscriptionService#assertCanAddUser}). The account being re-activated is already counted, so it is
     * refused only while the company has more accounts than the plan allows; delete accounts to get back within it.
     */
    public void assertCanActivate(Company company) {
        var e = subscriptions.effective(company);
        if (e.plan() == null || e.plan().getMaxUsers() == null) {
            return;
        }
        if (users.countByCompanyIdAndDeletedFalse(company.getId()) > e.plan().getMaxUsers()) {
            throw com.terp.erp.exception.ApiException.badRequest(ESaasError.SAAS_011, e.plan().getMaxUsers());
        }
    }

    private static boolean isAdmin(UserAccount u) {
        return u.getRoles().stream().anyMatch(r -> EDefaultRole.COMPANY_ADMIN.name().equals(r.getCode()));
    }
}
