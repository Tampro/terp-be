package com.terp.erp.iam.email;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.email.ENotificationEvent.ENotificationModule;
import com.terp.erp.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Company Settings → Email Notification Settings: one switch per toggleable platform template
 * ({@link EEmailTemplate}) or business event ({@link ENotificationEvent}). No row = on.
 */
@Service
@RequiredArgsConstructor
public class EmailNotificationService {
    /** dispatched: false = the switch is kept but nothing sends this email yet (needs the notification service). */
    public record NotificationDto(String code, String name, boolean enabled, boolean dispatched) {
    }

    public record NotificationGroup(ENotificationModule module, List<NotificationDto> notifications) {
    }

    public record EnabledDto(String code, boolean enabled) {
    }

    private final CompanyEmailNotificationRepository toggles;
    private final EmailTemplateRepository templates;

    @Transactional(readOnly = true)
    public List<NotificationGroup> list(Long requestedCompanyId) {
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        Map<String, Boolean> state = toggles.findByCompanyId(cid).stream()
                .collect(Collectors.toMap(CompanyEmailNotification::getTemplateCode,
                        CompanyEmailNotification::isEnabled, (a, b) -> a));
        Map<String, String> names = templates.findAll().stream()
                .collect(Collectors.toMap(EmailTemplate::getCode, EmailTemplate::getName, (a, b) -> a));
        Map<ENotificationModule, List<NotificationDto>> by = new EnumMap<>(ENotificationModule.class);
        for (ENotificationModule m : ENotificationModule.values()) {
            by.put(m, new ArrayList<>());
        }
        for (EEmailTemplate t : EEmailTemplate.values()) {
            if (t.isToggleable()) {
                by.get(moduleOf(t)).add(new NotificationDto(t.name(), names.getOrDefault(t.name(), t.name()),
                        state.getOrDefault(t.name(), true), true));
            }
        }
        for (ENotificationEvent e : ENotificationEvent.values()) {
            by.get(e.getModule()).add(new NotificationDto(e.name(), e.getDisplayName(),
                    state.getOrDefault(e.name(), true), false));
        }
        return by.entrySet().stream().filter(x -> !x.getValue().isEmpty())
                .map(x -> new NotificationGroup(x.getKey(), x.getValue())).toList();
    }

    /** The caller's own company (a super admin has none: IAM_003). */
    @Transactional
    public NotificationDto toggle(String code, boolean enabled) {
        NotificationDto known = describe(code);
        Long cid = TenantGuard.resolveCompany(null, EIamError.IAM_003);
        CompanyEmailNotification row = toggles.findByCompanyIdAndTemplateCode(cid, code).orElseGet(() -> {
            CompanyEmailNotification n = new CompanyEmailNotification();
            n.setCompanyId(cid);
            n.setTemplateCode(code);
            return n;
        });
        row.setEnabled(enabled);
        toggles.save(row);
        return new NotificationDto(code, known.name(), enabled, known.dispatched());
    }

    /** For the services that will send these emails: is it on for the caller's company? */
    @Transactional(readOnly = true)
    public EnabledDto enabled(Long requestedCompanyId, String code) {
        describe(code);
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        return new EnabledDto(code, toggles.findByCompanyIdAndTemplateCode(cid, code)
                .map(CompanyEmailNotification::isEnabled).orElse(true));
    }

    private NotificationDto describe(String code) {
        Optional<EEmailTemplate> t = Arrays.stream(EEmailTemplate.values()).filter(x -> x.name().equals(code))
                .findFirst();
        if (t.isPresent()) {
            if (!t.get().isToggleable()) {
                throw ApiException.badRequest(EEmailError.EMAIL_003);
            }
            String name = templates.findByCode(code).map(EmailTemplate::getName).orElse(code);
            return new NotificationDto(code, name, true, true);
        }
        return Arrays.stream(ENotificationEvent.values()).filter(x -> x.name().equals(code)).findFirst()
                .map(e -> new NotificationDto(code, e.getDisplayName(), true, false))
                .orElseThrow(() -> ApiException.notFound(EEmailError.EMAIL_001));
    }

    /** New user is the reference's "General → New User"; the plan / payout emails belong to the SaaS subscription. */
    static ENotificationModule moduleOf(EEmailTemplate t) {
        return t == EEmailTemplate.USER_CREATE ? ENotificationModule.GENERAL : ENotificationModule.SUBSCRIPTION;
    }
}
