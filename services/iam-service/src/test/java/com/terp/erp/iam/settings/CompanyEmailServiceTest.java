package com.terp.erp.iam.settings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.settings.CompanyEmailService.CompanyEmailForm;
import com.terp.erp.iam.settings.SettingsDtos.EMailEncryption;
import com.terp.erp.iam.user.UserProfileDeleteTest;

import java.time.Clock;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyEmailServiceTest {
    private final CompanyEmailSettingRepository repo = mock(CompanyEmailSettingRepository.class);
    private final SettingsService platform = mock(SettingsService.class);
    private final SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final CompanyEmailService service = new CompanyEmailService(repo, platform, cipher, Clock.systemUTC());
    private final SettingsService.Mailer platformMailer = SettingsService.buildMailer("smtp.platform.invalid", 587,
            null, null, EMailEncryption.TLS, "noreply@platform.invalid", "Platform");

    @BeforeEach
    void setUp() {
        UserProfileDeleteTest.login(1L, 10L, "IAM_SETTINGS_MANAGE");
        when(platform.mailer()).thenReturn(platformMailer);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void companySmtpWhenOnElseThePlatforms() {
        assertThat(service.mailerFor(10L)).isSameAs(platformMailer);
        assertThat(service.mailerFor(null)).isSameAs(platformMailer);
        CompanyEmailSetting s = new CompanyEmailSetting();
        s.setCompanyId(10L);
        when(repo.findById(10L)).thenReturn(Optional.of(s));
        var v = service.save(null, form(true, "s3cret"));
        assertThat(v.passwordSet()).isTrue();
        assertThat(v.usingPlatform()).isFalse();
        assertThat(s.getPasswordEnc()).doesNotContain("s3cret");
        when(repo.findById(10L)).thenReturn(Optional.of(s));
        var m = service.mailerFor(10L);
        assertThat(m.fromAddress()).isEqualTo("hr@company.invalid");
        assertThat(m.sender().getHost()).isEqualTo("smtp.company.invalid");
        assertThat(m.sender().getPassword()).isEqualTo("s3cret");
        // password omitted = kept; switched off = the platform's
        service.save(null, form(false, null));
        assertThat(cipher.decrypt(s.getPasswordEnc())).isEqualTo("s3cret");
        assertThat(service.mailerFor(10L)).isSameAs(platformMailer);
    }

    @Test
    void testWithoutCompanySmtpIsSettings003() {
        when(repo.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.sendTest(null, "me@example.invalid")).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ESettingsError.SETTINGS_003));
    }

    private static CompanyEmailForm form(boolean enabled, String password) {
        return new CompanyEmailForm(enabled, " smtp.company.invalid ", 587, "hr", password, EMailEncryption.TLS,
                "hr@company.invalid", "Company HR");
    }
}
