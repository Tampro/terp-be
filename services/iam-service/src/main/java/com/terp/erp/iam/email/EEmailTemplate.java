package com.terp.erp.iam.email;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * The templates the system sends, with the placeholders each one supports (docs/analysis/06 §3.3.1 "Available
 * Variables"). Texts live in the database; this list is the contract between the code that fills the values and
 * the super admin who writes the text. Every template also gets {@link #COMMON}.
 */
@Getter
@RequiredArgsConstructor
public enum EEmailTemplate {
    USER_CREATE(true, List.of("user_name", "username", "email", "temporary_password", "company_name")),
    // Sent to the first administrator of a new company; nothing to turn off yet.
    COMPANY_CREATE(false, List.of("user_name", "username", "email", "temporary_password", "company_name")),
    PLAN_ORDER_APPROVED(true, List.of("company_name", "order_number", "plan_name", "billing_cycle", "final_price", "expires_at")),
    PLAN_ORDER_REJECTED(true, List.of("company_name", "order_number", "plan_name", "notes")),
    PLAN_REQUEST_APPROVED(true, List.of("company_name", "plan_name", "billing_cycle", "notes")),
    PLAN_REQUEST_REJECTED(true, List.of("company_name", "plan_name", "notes")),
    PAYOUT_APPROVED(true, List.of("company_name", "amount", "notes")),
    PAYOUT_REJECTED(true, List.of("company_name", "amount", "notes")),
    // Account emails the user asked for or must act on: a company cannot switch them off.
    PASSWORD_RESET(false, List.of("user_name", "username", "company_name", "reset_link", "expires_minutes")),
    EMAIL_VERIFY(false, List.of("company_name", "verify_link", "expires_hours"));

    public static final List<String> COMMON = List.of("app_name", "app_url");

    /** Whether a company may switch it off (Company Settings → Email Notification Settings). */
    private final boolean toggleable;
    private final List<String> variables;

    public List<String> allVariables() {
        return java.util.stream.Stream.concat(COMMON.stream(), variables.stream()).toList();
    }
}
