package com.terp.erp.iam.saas;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Permission;
import com.terp.erp.iam.model.Plan;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps only the permissions the company's plan allows. Used wherever IAM turns roles into authorities (sign-in,
 * login-as-company), so the services' @PreAuthorize answer 403 for a module outside the plan.
 */
@Component
@RequiredArgsConstructor
public class PlanModuleFilter {
    private final SubscriptionService subscriptions;

    public Set<String> allowed(Company company, Collection<Permission> permissions) {
        Plan plan = subscriptions.effective(company).plan();
        return permissions.stream()
                .filter(p -> allows(plan, p.getModule()))
                .map(Permission::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * No plan (e.g. the bootstrap company) or a plan without a module list = every module, as before plans had
     * modules. A permission module no plan module governs stays allowed, so nothing breaks until it is mapped.
     */
    static boolean allows(Plan plan, String permissionModule) {
        if (plan == null || plan.getModules().isEmpty() || EPlanModule.ALWAYS_ON.contains(permissionModule)) {
            return true;
        }
        EPlanModule governing = EPlanModule.governing(permissionModule);
        return governing == null || plan.getModules().contains(governing);
    }
}
