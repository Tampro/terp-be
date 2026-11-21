package com.terp.erp.iam.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.repository.CurrencyRepository;
import com.terp.erp.iam.settings.SettingsDtos.EMailEncryption;
import com.terp.erp.iam.settings.SettingsDtos.EmailSettingsForm;
import com.terp.erp.iam.settings.SettingsDtos.ERecaptchaVersion;
import com.terp.erp.iam.settings.SettingsDtos.RecaptchaSettingsForm;
import com.terp.erp.iam.settings.SettingsDtos.StorageSettings;
import com.terp.erp.iam.settings.SettingsDtos.SystemSettings;

import java.time.Clock;
import java.time.DayOfWeek;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsServiceTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final Map<String, PlatformSetting> rows = new HashMap<>();
    private SettingsService service;

    @BeforeEach
    void setUp() {
        PlatformSettingRepository repo = mock(PlatformSettingRepository.class);
        when(repo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(rows.get((String) inv.getArgument(0))));
        when(repo.save(any(PlatformSetting.class))).thenAnswer(inv -> {
            PlatformSetting p = inv.getArgument(0);
            rows.put(p.getSection(), p);
            return p;
        });
        CurrencyRepository currencies = mock(CurrencyRepository.class);
        when(currencies.findFirstByDefaultCurrencyTrue()).thenReturn(Optional.empty());
        com.terp.erp.iam.language.LanguageRepository languages = mock(com.terp.erp.iam.language.LanguageRepository.class);
        com.terp.erp.iam.language.Language vi = new com.terp.erp.iam.language.Language();
        vi.setCode("vi");
        vi.setEnabled(true);
        when(languages.findByCode("vi")).thenReturn(Optional.of(vi));
        service = new SettingsService(repo, currencies, languages, new SecretCipher(KEY),
                new ObjectMapper().registerModule(new JavaTimeModule()), Clock.systemUTC());
    }

    @Test
    void unsavedSectionReadsAsDefaults() {
        assertThat(service.system().defaultTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(service.storage().allowedFileTypes()).contains("pdf");
    }

    @Test
    void unknownTimezoneIsRefused() {
        var s = new SystemSettings("vi", "dd/MM/yyyy", "HH:mm", DayOfWeek.MONDAY, "Mars/Olympus", false, true, false, null,
                true);
        assertError(() -> service.saveSystem(s), ESettingsError.SETTINGS_001);
    }

    @Test
    void smtpPasswordIsEncryptedWriteOnlyAndKeptWhenOmitted() {
        service.saveEmail(new EmailSettingsForm("smtp.example.com", 587, "u", "s3cret!", EMailEncryption.TLS,
                "noreply@example.com", "ERP"));
        String json = rows.get("EMAIL").getValueJson();
        assertThat(json).doesNotContain("s3cret!");
        assertThat(service.email().passwordSet()).isTrue();

        service.saveEmail(new EmailSettingsForm("smtp2.example.com", 465, "u", null, EMailEncryption.SSL,
                "noreply@example.com", "ERP"));
        assertThat(service.email().passwordSet()).isTrue(); // omitted = kept
        assertThat(service.email().host()).isEqualTo("smtp2.example.com");

        service.saveEmail(new EmailSettingsForm("smtp2.example.com", 465, "u", "", EMailEncryption.SSL,
                "noreply@example.com", "ERP"));
        assertThat(service.email().passwordSet()).isFalse(); // empty = removed
    }

    @Test
    void recaptchaNeedsSiteKeyAndSecretToBeEnabled() {
        assertError(() -> service.saveRecaptcha(new RecaptchaSettingsForm(true, ERecaptchaVersion.V2, "site", null)),
                ESettingsError.SETTINGS_006);
        service.saveRecaptcha(new RecaptchaSettingsForm(true, ERecaptchaVersion.V2, "site", "secret-value"));
        assertThat(service.activeRecaptcha().secret()).isEqualTo("secret-value");
        assertThat(service.publicSettings().recaptcha().siteKey()).isEqualTo("site");
        assertThat(rows.get("RECAPTCHA").getValueJson()).doesNotContain("secret-value");
    }

    @Test
    void storageOnlyAcceptsTypesMediaServiceCanVerify() {
        assertError(() -> service.saveStorage(new StorageSettings(List.of("png", "SVG", "exe"), 2048)),
                ESettingsError.SETTINGS_005);
        assertThat(service.saveStorage(new StorageSettings(List.of("PNG", "pdf", "png"), 2048)).allowedFileTypes())
                .containsExactly("png", "pdf");
    }

    @Test
    void secretsCannotBeSavedWithoutAKey() {
        SecretCipher noKey = new SecretCipher("");
        assertThat(noKey.available()).isFalse();
        assertError(() -> noKey.encrypt("x"), ESettingsError.SETTINGS_002);
    }

    @Test
    void cipherDetectsAnotherKeyOrTampering() {
        SecretCipher a = new SecretCipher(KEY);
        byte[] other = new byte[32];
        other[0] = 1;
        SecretCipher b = new SecretCipher(Base64.getEncoder().encodeToString(other));
        String enc = a.encrypt("hello");
        assertThat(a.decrypt(enc)).isEqualTo("hello");
        assertError(() -> b.decrypt(enc), ESettingsError.SETTINGS_007);
    }

    private static void assertError(Runnable call, ESettingsError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
