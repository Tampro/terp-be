package com.terp.erp.iam.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.terp.erp.iam.email.EPlatformEmail;
import com.terp.erp.iam.repository.CurrencyRepository;
import com.terp.erp.iam.settings.SettingsDtos.PlatformEmailNotificationSettings;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformEmailNotificationsTest {
    private final Map<String, PlatformSetting> rows = new HashMap<>();
    private SettingsService service;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @BeforeEach
    void setUp() {
        PlatformSettingRepository repo = mock(PlatformSettingRepository.class);
        when(repo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(rows.get((String) inv.getArgument(0))));
        when(repo.save(any(PlatformSetting.class))).thenAnswer(inv -> {
            PlatformSetting p = inv.getArgument(0);
            rows.put(p.getSection(), p);
            return p;
        });
        service = new SettingsService(repo, mock(CurrencyRepository.class),
                mock(com.terp.erp.iam.language.LanguageRepository.class),
                new SecretCipher(Base64.getEncoder().encodeToString(new byte[32])),
                new ObjectMapper().registerModule(new JavaTimeModule()), Clock.systemUTC());
    }

    @Test
    void everySwitchIsOnByDefaultWithNoRecipients() {
        PlatformEmailNotificationSettings s = service.platformEmailNotifications();
        assertThat(s.newUser()).isTrue();
        assertThat(s.helpdeskTicketCreated()).isTrue();
        assertThat(s.helpdeskTicketReply()).isTrue();
        assertThat(s.helpdeskNotifyEmails()).isEmpty();
        for (EPlatformEmail e : EPlatformEmail.values()) {
            assertThat(service.platformEmailDisabled(e)).as(e.name()).isFalse();
        }
    }

    @Test
    void savedSwitchesAreHonouredAndAddressesNormalised() {
        service.savePlatformEmailNotifications(new PlatformEmailNotificationSettings(false, true, false,
                List.of(" Support@Example.com ", "support@example.com", "ops@example.com")));

        assertThat(service.platformEmailDisabled(EPlatformEmail.NEW_USER)).isTrue();
        assertThat(service.platformEmailDisabled(EPlatformEmail.HELPDESK_TICKET_CREATED)).isFalse();
        assertThat(service.platformEmailDisabled(EPlatformEmail.HELPDESK_TICKET_REPLY)).isTrue();
        assertThat(service.platformEmailNotifications().helpdeskNotifyEmails())
                .containsExactly("support@example.com", "ops@example.com");
    }

    @Test
    void anOldRowWithoutASwitchReadsItAsOn() {
        PlatformSetting row = new PlatformSetting();
        row.setSection("PLATFORM_EMAIL_NOTIFICATIONS");
        row.setValueJson("{\"newUser\":false}");
        rows.put(row.getSection(), row);

        PlatformEmailNotificationSettings s = service.platformEmailNotifications();
        assertThat(s.newUser()).isFalse();
        assertThat(s.helpdeskTicketCreated()).isTrue();
        assertThat(s.helpdeskNotifyEmails()).isEmpty();
    }

    @Test
    void recipientsAreValidatedAddressesAtMostTen() {
        assertThat(validator.validate(new PlatformEmailNotificationSettings(true, true, true,
                List.of("support@example.com")))).isEmpty();
        assertThat(validator.validate(new PlatformEmailNotificationSettings(true, true, true,
                List.of("not-an-address")))).isNotEmpty();
        List<String> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            eleven.add("a" + i + "@example.com");
        }
        assertThat(validator.validate(new PlatformEmailNotificationSettings(true, true, true, eleven))).isNotEmpty();
        assertThat(validator.validate(new PlatformEmailNotificationSettings(null, true, true, List.of())))
                .isNotEmpty();
    }

    @Test
    void platformSettingsEndpointsAreSuperAdminOnly() throws Exception {
        for (String name : List.of("platformEmailNotifications", "savePlatformEmailNotifications")) {
            var m = java.util.Arrays.stream(SettingsController.class.getMethods())
                    .filter(x -> x.getName().equals(name)).findFirst().orElseThrow();
            assertThat(m.getAnnotation(PreAuthorize.class).value()).isEqualTo("@PermissionChecker.isSuperAdmin()");
        }
        for (var m : CacheAdminController.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                assertThat(m.getAnnotation(PreAuthorize.class).value()).as(m.getName())
                        .isEqualTo("@PermissionChecker.isSuperAdmin()");
            }
        }
    }
}
