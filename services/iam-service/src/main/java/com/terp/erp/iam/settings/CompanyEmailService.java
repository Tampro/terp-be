package com.terp.erp.iam.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.common.Nulls;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.settings.SettingsDtos.EMailEncryption;
import com.terp.erp.iam.settings.SettingsService.Mailer;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;

/**
 * Company Email Settings (docs/analysis/06 §10.5.4; PO 2026-11-21): a company may send its emails through its own
 * SMTP; without one (or switched off) the platform SMTP is used. The password is encrypted with ERP_SETTINGS_ENC_KEY
 * and never returned (passwordSet).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyEmailService {
    private final CompanyEmailSettingRepository repo;
    private final SettingsService platform;
    private final SecretCipher cipher;
    private final Clock clock;

    public record CompanyEmailForm(
            @Schema(description = "Use this SMTP for the company's emails; false = the platform's")
            @NotNull Boolean enabled,
            @Schema(example = "smtp.example.com") @NotBlank @Size(max = 255) String host,
            @Schema(example = "587") @NotNull @Min(1) @Max(65535) Integer port,
            @Size(max = 255) String username,
            @Schema(description = "Write-only. Omitted = keep the stored one; empty string = remove it.")
            @Size(max = 255) String password,
            @NotNull EMailEncryption encryption,
            @Schema(example = "hr@example.com") @NotBlank @Email String fromAddress,
            @Size(max = 100) String fromName) {
    }

    /**
     * configured = the company has its own SMTP; usingPlatform = the platform SMTP sends the company's emails;
     * smtpConfigured = some SMTP (its own, switched on, or the platform's) can send them — false means event emails
     * (welcome, password reset, …) are skipped.
     */
    public record CompanyEmailView(boolean configured, boolean enabled, String host, Integer port, String username,
                                   boolean passwordSet, EMailEncryption encryption, String fromAddress,
                                   String fromName, boolean usingPlatform, boolean smtpConfigured) {
    }

    @Transactional(readOnly = true)
    public CompanyEmailView get(Long requestedCompanyId) {
        Long companyId = company(requestedCompanyId);
        return repo.findById(Nulls.nonNull(companyId)).map(this::view)
                .orElse(new CompanyEmailView(false, false, null, 587, null, false, EMailEncryption.TLS, null, null,
                        true, platform.mailer() != null));
    }

    @Transactional
    public CompanyEmailView save(Long requestedCompanyId, CompanyEmailForm f) {
        Long companyId = company(requestedCompanyId);
        CompanyEmailSetting s = repo.findById(Nulls.nonNull(companyId)).orElseGet(() -> {
            CompanyEmailSetting n = new CompanyEmailSetting();
            n.setCompanyId(companyId);
            return n;
        });
        if (f.password() != null) {
            s.setPasswordEnc(f.password().isEmpty() ? null : cipher.encrypt(f.password()));
        }
        s.setEnabled(f.enabled());
        s.setHost(f.host().trim());
        s.setPort(f.port());
        s.setUsername(f.username() == null || f.username().isBlank() ? null : f.username().trim());
        s.setEncryption(f.encryption());
        s.setFromAddress(f.fromAddress().trim());
        s.setFromName(f.fromName() == null || f.fromName().isBlank() ? null : f.fromName().trim());
        s.setUpdatedBy(CurrentUser.find().map(p -> p.keycloakId()).orElse(null));
        s.setUpdatedDate(clock.instant());
        return view(repo.save(s));
    }

    /** Back to the platform SMTP: the company's settings are removed. */
    @Transactional
    public void delete(Long requestedCompanyId) {
        Long companyId = company(requestedCompanyId);
        repo.findById(Nulls.nonNull(companyId)).ifPresent(repo::delete);
    }

    /** Send Test Email through the company's SMTP (not the platform's), even when it is switched off. */
    @Transactional(readOnly = true)
    public void sendTest(Long requestedCompanyId, String to) {
        Long companyId = company(requestedCompanyId);
        CompanyEmailSetting s = repo.findById(Nulls.nonNull(companyId)).orElse(null);
        SettingsService.sendTest(s == null ? null : mailer(s), to,
                "This is a test email from the ERP company email settings.");
    }

    /** The sender of a company's emails: its own SMTP when configured and on, else the platform's (null = none). */
    @Transactional(readOnly = true)
    public Mailer mailerFor(Long companyId) {
        if (companyId != null) {
            CompanyEmailSetting s = repo.findById(Nulls.nonNull(companyId)).orElse(null);
            if (s != null && s.isEnabled()) {
                try {
                    return mailer(s);
                } catch (RuntimeException e) {
                    // An unreadable password (key rotated): fall back rather than lose the email.
                    log.warn("Company {} SMTP unusable ({}), using the platform SMTP", companyId,
                            e.getClass().getSimpleName());
                }
            }
        }
        return platform.mailer();
    }

    private Mailer mailer(CompanyEmailSetting s) {
        return SettingsService.buildMailer(s.getHost(), s.getPort(), s.getUsername(),
                s.getPasswordEnc() == null ? null : cipher.decrypt(s.getPasswordEnc()), s.getEncryption(),
                s.getFromAddress(), s.getFromName());
    }

    /** Its own SMTP counts as soon as it is on (the password is not decrypted here); else the platform's decides. */
    private CompanyEmailView view(CompanyEmailSetting s) {
        return new CompanyEmailView(true, s.isEnabled(), s.getHost(), s.getPort(), s.getUsername(),
                s.getPasswordEnc() != null, s.getEncryption(), s.getFromAddress(), s.getFromName(), !s.isEnabled(),
                s.isEnabled() || platform.mailer() != null);
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EIamError.IAM_003);
    }
}
