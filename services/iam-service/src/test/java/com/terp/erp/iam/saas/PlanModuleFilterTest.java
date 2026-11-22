package com.terp.erp.iam.saas;

import org.junit.jupiter.api.Test;
import com.terp.erp.iam.constant.IamPermissions;
import com.terp.erp.iam.helpdesk.HelpdeskPermissions;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Permission;
import com.terp.erp.iam.model.Plan;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlanModuleFilterTest {
    /** Permission modules seeded today; a new one must be mapped in EPlanModule or listed as always on. */
    private static final Set<String> SEEDED_MODULES = Set.of("ANNOUNCEMENT", "ASSET", "ATTENDANCE", "CONTRACT",
            "DOCUMENT", "EMPLOYEE", "EVENT", "HELPDESK", "IAM", "LEAVE", "LIFECYCLE", "MEDIA", "MEETING", "NOTIFICATION",
            "ORG", "PAYROLL", "PERFORMANCE", "RECRUITMENT", "SAAS", "TRAINING", "ZOOM", "INVENTORY", "SALES", "CUSTOMER",
            "QUOTATION", "CRM", "CRM_CONTRACT", "PURCHASE", "VENDOR", "POS", "BANK", "PROJECT", "FORM", "COA", "REVENUE", "EXPENSE", "PAYMENT", "NOTE", "ACCOUNTING_REPORT");

    /** A filter for a company without a plan: every permission passes (used by other tests). */
    public static PlanModuleFilter noPlan() {
        return filterFor(null);
    }

    static PlanModuleFilter filterFor(Plan plan) {
        SubscriptionService subs = mock(SubscriptionService.class);
        when(subs.effective(any())).thenReturn(new SubscriptionService.Effective(
                plan == null ? ESubscriptionStatus.NONE : ESubscriptionStatus.ACTIVE, plan, Instant.now(), false));
        return new PlanModuleFilter(subs);
    }

    @Test
    void planWithoutHrmLosesHrPermissionsButKeepsAccountsSubscriptionAndHelpdesk() {
        Plan plan = new Plan();
        plan.getModules().addAll(Set.of(EPlanModule.RECRUITMENT, EPlanModule.ACCOUNTING));
        List<Permission> perms = List.of(perm("EMPLOYEE_VIEW", "EMPLOYEE"), perm("PAYROLL_VIEW", "PAYROLL"),
                perm("RECRUITMENT_VIEW", "RECRUITMENT"), perm(IamPermissions.USER_VIEW, "IAM"),
                perm("SAAS_ORDER_CREATE", "SAAS"), perm(HelpdeskPermissions.VIEW, "HELPDESK"),
                perm("MEDIA_VIEW", "MEDIA"), perm("TRAINING_VIEW", "TRAINING"));

        Set<String> allowed = filterFor(plan).allowed(new Company(), perms);

        assertThat(allowed).containsExactlyInAnyOrder("RECRUITMENT_VIEW", IamPermissions.USER_VIEW,
                "SAAS_ORDER_CREATE", HelpdeskPermissions.VIEW, "MEDIA_VIEW");
    }

    @Test
    void noPlanOrAPlanWithoutModulesAllowsEverything() {
        List<Permission> perms = List.of(perm("EMPLOYEE_VIEW", "EMPLOYEE"), perm("ZOOM_VIEW", "ZOOM"));

        assertThat(noPlan().allowed(new Company(), perms)).containsExactlyInAnyOrder("EMPLOYEE_VIEW", "ZOOM_VIEW");
        assertThat(filterFor(new Plan()).allowed(new Company(), perms))
                .containsExactlyInAnyOrder("EMPLOYEE_VIEW", "ZOOM_VIEW");
    }

    @Test
    void everySeededPermissionModuleIsGovernedOrAlwaysOn() {
        for (String m : SEEDED_MODULES) {
            assertThat(EPlanModule.ALWAYS_ON.contains(m) || EPlanModule.governing(m) != null)
                    .as("permission module %s has no plan module", m).isTrue();
        }
    }

    @Test
    void aPermissionModuleBelongsToAtMostOnePlanModule() {
        List<String> all = Arrays.stream(EPlanModule.values()).flatMap(m -> m.getPermissionModules().stream()).toList();
        assertThat(all).doesNotHaveDuplicates();
        assertThat(all).doesNotContainAnyElementsOf(EPlanModule.ALWAYS_ON);
    }

    @Test
    void catalogueListsTheTwentyThreeModulesOfThePlans() {
        assertThat(EPlanModule.values()).hasSize(23);
        assertThat(EPlanModule.HRM.available()).isTrue();
        assertThat(EPlanModule.POS.available()).isTrue();
        assertThat(EPlanModule.PROJECT.available()).isTrue();
        assertThat(EPlanModule.SLACK.available()).isFalse();
    }

    private static Permission perm(String code, String module) {
        Permission p = new Permission();
        p.setCode(code);
        p.setModule(module);
        return p;
    }
}
