package com.terp.erp.iam.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.email.EmailNotifier;
import com.terp.erp.iam.model.AccountToken;
import com.terp.erp.iam.model.AccountToken.EPurpose;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.repository.AccountTokenRepository;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.settings.SettingsService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Email verification of a company (docs/analysis/06 §2.5): when System Settings → Email Verification is on, a new
 * company gets a one-time link at its email. Only a self-registered company is refused at login until it is
 * verified (PO 2026-11-21); one created by the super admin just gets the email.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    static final Duration VALIDITY = Duration.ofHours(24);

    private final AccountTokenRepository tokens;
    private final CompanyRepository companies;
    private final EmailNotifier notifier;
    private final SettingsService settings;
    private final Clock clock;

    @Value("${erp.app-url:http://localhost:5173}")
    private String appUrl;

    public boolean enabled() {
        return settings.system().emailVerification();
    }

    /** Sends a new link (earlier ones stop working). Call inside the company's transaction; sent after commit. */
    @Transactional
    public void issue(Company c) {
        if (c.getEmail() == null || c.getEmail().isBlank()) {
            return;
        }
        Instant now = clock.instant();
        tokens.findByPurposeAndCompanyIdAndUsedAtIsNull(EPurpose.EMAIL_VERIFY, c.getId()).forEach(t -> t.setUsedAt(now));
        String token = AccountTokens.newToken();
        AccountToken t = new AccountToken();
        t.setPurpose(EPurpose.EMAIL_VERIFY);
        t.setCompanyId(c.getId());
        t.setTokenHash(AccountTokens.hash(token));
        t.setExpiresAt(now.plus(VALIDITY));
        tokens.save(t);
        notifier.emailVerify(c, appUrl + "/verify-email?token=" + token, VALIDITY.toHours());
    }

    /** One-time: the link is spent and the company may log in (AUTH_014 when unknown, used or expired). */
    @Transactional
    public void verify(String token) {
        Instant now = clock.instant();
        AccountToken t = tokens.findByTokenHash(AccountTokens.hash(token))
                .filter(x -> x.getPurpose() == EPurpose.EMAIL_VERIFY && x.getUsedAt() == null
                        && x.getExpiresAt().isAfter(now))
                .orElseThrow(() -> ApiException.badRequest(EIamError.AUTH_014));
        t.setUsedAt(now);
        Company c = companies.findByIdAndDeletedFalse(t.getCompanyId())
                .orElseThrow(() -> ApiException.badRequest(EIamError.AUTH_014));
        c.setEmailVerifiedAt(now);
        c.setEmailVerifyRequired(false);
        log.info("Company {} verified its email", c.getCode());
    }

    /** A new link for a company still waiting; silent otherwise, so the answer never tells which emails exist. */
    @Transactional
    public void resend(String email) {
        companies.findByEmailIgnoreCaseAndDeletedFalse(email.trim()).stream()
                .filter(c -> c.getEmailVerifiedAt() == null)
                .forEach(this::issue);
    }
}
