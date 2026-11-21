package com.terp.erp.iam.email;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import com.terp.erp.iam.constant.EDefaultRole;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.UserAccountRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One method per business event: picks the recipients and the values, then publishes an EmailEvent that
 * EmailDispatcher sends after commit. Company events go to the company's active COMPANY_ADMIN accounts.
 */
@Component
@RequiredArgsConstructor
public class EmailNotifier {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);

    private final ApplicationEventPublisher events;
    private final UserAccountRepository users;

    /** New account of an existing company (not the first admin of a new company — that is companyCreated). */
    public void userCreated(UserAccount u, Company c, String temporaryPassword) {
        publish(EEmailTemplate.USER_CREATE, c.getId(), List.of(u.getEmail()), account(u, c, temporaryPassword));
    }

    public void companyCreated(UserAccount admin, Company c, String temporaryPassword) {
        publish(EEmailTemplate.COMPANY_CREATE, null, List.of(admin.getEmail()), account(admin, c, temporaryPassword));
    }

    public void orderDecided(boolean approved, Company c, String orderNumber, String planName, String cycle,
                             BigDecimal finalPrice, Instant expiresAt, String notes) {
        Map<String, String> v = base(c);
        v.put("order_number", orderNumber);
        v.put("plan_name", planName);
        v.put("billing_cycle", cycle);
        v.put("final_price", money(finalPrice));
        v.put("expires_at", expiresAt == null ? "" : DATE.format(expiresAt));
        v.put("notes", notes == null ? "" : notes);
        publish(approved ? EEmailTemplate.PLAN_ORDER_APPROVED : EEmailTemplate.PLAN_ORDER_REJECTED, c.getId(),
                admins(c), v);
    }

    public void planRequestDecided(boolean approved, Company c, String planName, String cycle, String notes) {
        Map<String, String> v = base(c);
        v.put("plan_name", planName);
        v.put("billing_cycle", cycle);
        v.put("notes", notes == null ? "" : notes);
        publish(approved ? EEmailTemplate.PLAN_REQUEST_APPROVED : EEmailTemplate.PLAN_REQUEST_REJECTED, c.getId(),
                admins(c), v);
    }

    public void payoutDecided(boolean approved, Company c, BigDecimal amount, String notes) {
        Map<String, String> v = base(c);
        v.put("amount", money(amount));
        v.put("notes", notes == null ? "" : notes);
        publish(approved ? EEmailTemplate.PAYOUT_APPROVED : EEmailTemplate.PAYOUT_REJECTED, c.getId(), admins(c), v);
    }

    /** The one-time link of Forgot password (docs/analysis/06 §2.4), to the account's own email. */
    public void passwordReset(UserAccount u, Company c, String link, long minutes) {
        Map<String, String> v = base(c);
        v.put("user_name", u.getFullName());
        v.put("username", u.getUsername());
        v.put("reset_link", link);
        v.put("expires_minutes", String.valueOf(minutes));
        publish(EEmailTemplate.PASSWORD_RESET, c.getId(), List.of(u.getEmail()), v);
    }

    /** Email verification of a new company (§2.5), to the company's email. */
    public void emailVerify(Company c, String link, long hours) {
        Map<String, String> v = base(c);
        v.put("verify_link", link);
        v.put("expires_hours", String.valueOf(hours));
        publish(EEmailTemplate.EMAIL_VERIFY, c.getId(), List.of(c.getEmail()), v);
    }

    private Map<String, String> account(UserAccount u, Company c, String temporaryPassword) {
        Map<String, String> v = base(c);
        v.put("user_name", u.getFullName());
        v.put("username", u.getUsername());
        v.put("email", u.getEmail());
        v.put("temporary_password", temporaryPassword);
        return v;
    }

    private static Map<String, String> base(Company c) {
        Map<String, String> v = new HashMap<>();
        v.put("company_name", c.getName());
        return v;
    }

    private List<String> admins(Company c) {
        return users.findByCompanyAndRole(c.getId(), EDefaultRole.COMPANY_ADMIN.name()).stream()
                .filter(u -> u.getStatus() == EUserStatus.ACTIVE).map(UserAccount::getEmail).toList();
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "" : String.format("%,.0f", amount).replace(',', '.');
    }

    private void publish(EEmailTemplate t, Long companyId, List<String> to, Map<String, String> values) {
        events.publishEvent(new EmailDispatcher.EmailEvent(t, companyId, to, values));
    }
}
