package com.terp.erp.iam.saas;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EDefaultRole;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.keycloak.KeycloakClient;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanSeatEnforcerTest {
    private SubscriptionService subscriptions;
    private UserAccountRepository users;
    private KeycloakClient keycloak;
    private PlanSeatEnforcer enforcer;
    private Company company;
    private final List<UserAccount> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        subscriptions = mock(SubscriptionService.class);
        users = mock(UserAccountRepository.class);
        keycloak = mock(KeycloakClient.class);
        enforcer = new PlanSeatEnforcer(subscriptions, users, keycloak, mock(LocalAuthorityResolver.class));
        company = new Company();
        company.setId(7L);
        when(users.findByCompanyIdAndDeletedFalse(7L)).thenReturn(rows);
    }

    private void limit(Integer max) {
        Plan p = new Plan();
        p.setMaxUsers(max);
        when(subscriptions.effective(company)).thenReturn(new SubscriptionService.Effective(
                ESubscriptionStatus.ACTIVE, p, null, false));
    }

    private UserAccount user(long id, int day, boolean admin, EUserStatus status) {
        UserAccount u = new UserAccount();
        u.setId(id);
        u.setKeycloakId("kc-" + id);
        u.setStatus(status);
        u.setCreatedDate(Instant.parse("2026-11-01T00:00:00Z").plusSeconds(day * 86_400L));
        if (admin) {
            Role r = new Role();
            r.setCode(EDefaultRole.COMPANY_ADMIN.name());
            u.setRoles(Set.of(r));
        }
        rows.add(u);
        return u;
    }

    @Test
    void downgradeLocksTheNewestActiveAccountsButKeepsAdmins() {
        UserAccount oldStaff = user(1, 1, false, EUserStatus.ACTIVE);
        UserAccount newAdmin = user(2, 9, true, EUserStatus.ACTIVE);
        UserAccount midStaff = user(3, 5, false, EUserStatus.ACTIVE);
        UserAccount locked = user(4, 2, false, EUserStatus.LOCKED);
        limit(2);

        assertThat(enforcer.enforce(company)).isEqualTo(1);

        assertThat(newAdmin.getStatus()).isEqualTo(EUserStatus.ACTIVE);
        assertThat(oldStaff.getStatus()).isEqualTo(EUserStatus.ACTIVE);
        assertThat(midStaff.getStatus()).isEqualTo(EUserStatus.LOCKED);
        assertThat(locked.getStatus()).isEqualTo(EUserStatus.LOCKED);
        verify(keycloak).setEnabled("kc-3", false);
        verify(keycloak, never()).setEnabled(eq("kc-1"), anyBoolean());
    }

    @Test
    void withinTheLimitOrUnlimitedNothingChanges() {
        user(1, 1, false, EUserStatus.ACTIVE);
        limit(5);
        assertThat(enforcer.enforce(company)).isZero();
        limit(null);
        assertThat(enforcer.enforce(company)).isZero();
        verify(keycloak, never()).setEnabled(any(), anyBoolean());
    }

    @Test
    void keycloakFailureStillLocksInIam() {
        user(1, 1, true, EUserStatus.ACTIVE);
        UserAccount extra = user(2, 2, false, EUserStatus.ACTIVE);
        limit(1);
        doThrow(new IllegalStateException("down")).when(keycloak).setEnabled("kc-2", false);

        assertThat(enforcer.enforce(company)).isEqualTo(1);
        assertThat(extra.getStatus()).isEqualTo(EUserStatus.LOCKED);
    }

    @Test
    void reactivationIsRefusedAtTheLimit() {
        user(1, 1, true, EUserStatus.ACTIVE);
        user(2, 2, false, EUserStatus.LOCKED);
        limit(1);
        assertThatThrownBy(() -> enforcer.assertCanActivate(company))
                .isInstanceOf(ApiException.class).hasMessageContaining("SAAS_011");
        limit(2);
        enforcer.assertCanActivate(company);
    }
}
