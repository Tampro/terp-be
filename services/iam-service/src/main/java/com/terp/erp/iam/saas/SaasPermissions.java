package com.terp.erp.iam.saas;

import java.util.Set;

/**
 * Company-side SaaS permissions; must match the Liquibase seed. Plan, coupon and order administration is
 * super-admin only and has no code. {@link #ALL} is also the whole permission set a COMPANY_ADMIN keeps while the
 * company's subscription is expired — enough to see the plan and renew it.
 */
public final class SaasPermissions {
    public static final String SUBSCRIPTION_VIEW = "SAAS_SUBSCRIPTION_VIEW";
    public static final String ORDER_VIEW = "SAAS_ORDER_VIEW";
    public static final String ORDER_CREATE = "SAAS_ORDER_CREATE";

    public static final Set<String> ALL = Set.of(SUBSCRIPTION_VIEW, ORDER_VIEW, ORDER_CREATE);

    private SaasPermissions() {
    }
}
