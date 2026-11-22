package com.terp.erp.iam.email;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.terp.erp.iam.settings.SettingsService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Sends a templated email after the business transaction committed, on another thread: a mail problem never undoes
 * or slows the action, and nothing is sent for an action that rolled back. Failures are logged without addresses
 * or values (they may hold a temporary password).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDispatcher {
    private final EmailTemplateService templates;
    private final CompanyEmailNotificationRepository toggles;
    private final SettingsService settings;
    private final com.terp.erp.iam.settings.CompanyEmailService companyMail;

    /**
     * @param companyId company whose notification switch applies; null = not switchable
     * @param to        recipients (each gets the same message)
     * @param values    template values; never logged
     */
    public record EmailEvent(EEmailTemplate template, Long companyId, List<String> to, Map<String, String> values) {
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(EmailEvent e) {
        try {
            send(e);
        } catch (RuntimeException ex) {
            log.warn("Email {} not sent: {}", e.template(), ex.getClass().getSimpleName());
        }
    }

    void send(EmailEvent e) {
        List<String> to = e.to().stream().filter(a -> a != null && !a.isBlank()).distinct().toList();
        if (to.isEmpty()) {
            return;
        }
        if (e.template().isToggleable() && e.companyId() != null && toggles
                .findByCompanyIdAndTemplateCode(e.companyId(), e.template().name())
                .map(t -> !t.isEnabled()).orElse(false)) {
            log.info("Email {} switched off by company {}", e.template(), e.companyId());
            return;
        }
        // The company's own SMTP when it set one up (and it is on), else the platform's.
        SettingsService.Mailer mailer = companyMail.mailerFor(e.companyId());
        if (mailer == null) {
            // WARN: a silently dropped welcome / reset email looks like a bug to users; admins see smtpConfigured
            // = false on GET /api/v1/settings/company/email. Recipients are never logged.
            log.warn("Email {} skipped for company {}: SMTP not configured", e.template(), e.companyId());
            return;
        }
        var rendered = templates.render(e.template().name(), settings.system().defaultLanguage(), e.values());
        if (rendered.isEmpty()) {
            log.warn("Email {} skipped: no template text", e.template());
            return;
        }
        try {
            MimeMessage msg = mailer.sender().createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name());
            String fromName = rendered.get().fromName().isBlank() ? mailer.fromName() : rendered.get().fromName();
            h.setFrom(new InternetAddress(mailer.fromAddress(), fromName, StandardCharsets.UTF_8.name()));
            h.setTo(to.toArray(String[]::new));
            h.setSubject(rendered.get().subject());
            h.setText(rendered.get().html(), true);
            mailer.sender().send(msg);
            log.info("Email {} sent to {} recipient(s)", e.template(), to.size());
        } catch (Exception ex) {
            log.warn("Email {} failed: {}", e.template(), ex.getClass().getSimpleName());
        }
    }
}
