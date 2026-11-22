package com.terp.erp.iam.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.integration.IntegrationDtos.AiForm;
import com.terp.erp.iam.integration.IntegrationDtos.EAiProvider;
import com.terp.erp.iam.integration.IntegrationDtos.GoogleCalendarForm;
import com.terp.erp.iam.integration.IntegrationDtos.SlackForm;
import com.terp.erp.iam.integration.IntegrationDtos.TelegramForm;
import com.terp.erp.iam.integration.IntegrationDtos.TwilioForm;
import com.terp.erp.iam.settings.CompanySetting;
import com.terp.erp.iam.settings.CompanySettingRepository;
import com.terp.erp.iam.settings.ESettingsError;
import com.terp.erp.iam.settings.SecretCipher;
import com.terp.erp.iam.user.UserProfileDeleteTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.InetAddress;
import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyIntegrationServiceTest {
    private static final String SLACK_URL = "https://hooks.slack.com/services/T000/B000/SECRETPART";
    private static final String BOT_TOKEN = "123456:ABCDEFGHIJKLMNOPQRSTUVWXYZ_abc";

    /** company:section → row */
    private final Map<String, CompanySetting> rows = new HashMap<>();
    private final CompanySettingRepository repo = mock(CompanySettingRepository.class);
    private final SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final ObjectMapper mapper = new ObjectMapper();
    private final UrlGuard guard = new UrlGuard(host -> new InetAddress[]{InetAddress.getByName("54.230.10.10")});
    private final OutboundHttp http = mock(OutboundHttp.class);
    private final CompanyIntegrationService service = new CompanyIntegrationService(repo, cipher, mapper, guard, http);

    @BeforeEach
    void setUp() {
        UserProfileDeleteTest.login(1L, 10L, "IAM_SETTINGS_MANAGE");
        when(repo.findByCompanyIdAndSection(anyLong(), anyString()))
                .thenAnswer(i -> Optional.ofNullable(rows.get(i.getArgument(0) + ":" + i.getArgument(1))));
        when(repo.save(any())).thenAnswer(i -> {
            CompanySetting s = i.getArgument(0);
            rows.put(s.getCompanyId() + ":" + s.getSection(), s);
            return s;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String everythingStored() {
        return String.join("\n", rows.values().stream().map(CompanySetting::getValueJson).toList());
    }

    @Test
    void secretsAreEncryptedNeverReturnedAndKeptWhenBlank() throws Exception {
        service.saveSlack(null, new SlackForm(true, SLACK_URL));
        service.saveTelegram(null, new TelegramForm(true, BOT_TOKEN, "-100123"));
        service.saveTwilio(null, new TwilioForm(true, "AC" + "0".repeat(32), "twilio-secret-token", "+15005550006"));
        service.saveAi(null, new AiForm(true, EAiProvider.OPENAI, "sk-test-secret-key", "gpt-4o-mini"));
        String key = "{\"type\":\"service_account\",\"client_email\":\"bot@test-project.iam.gserviceaccount.com\","
                + "\"private_key\":\"-----BEGIN PRIVATE KEY-----\\nFAKE\\n-----END PRIVATE KEY-----\\n\"}";
        service.saveGoogleCalendar(null, new GoogleCalendarForm(true, "team@group.calendar.google.com", key));

        String stored = everythingStored();
        assertThat(stored).doesNotContain("SECRETPART", BOT_TOKEN, "twilio-secret-token", "sk-test-secret-key",
                "FAKE");
        var view = service.all(null);
        String json = mapper.writeValueAsString(view);
        assertThat(json).doesNotContain("SECRETPART", BOT_TOKEN, "twilio-secret-token", "sk-test-secret-key",
                "FAKE", "Enc");
        assertThat(view.slack().hasWebhookUrl()).isTrue();
        assertThat(view.telegram().hasBotToken()).isTrue();
        assertThat(view.telegram().chatId()).isEqualTo("-100123");
        assertThat(view.twilio().hasAuthToken()).isTrue();
        assertThat(view.aiAssistant().hasApiKey()).isTrue();
        assertThat(view.googleCalendar().serviceAccountEmail()).isEqualTo("bot@test-project.iam.gserviceaccount.com");

        // Blank / omitted secret keeps the stored one.
        service.saveSlack(null, new SlackForm(false, "  "));
        service.saveTelegram(null, new TelegramForm(true, null, "-100999"));
        service.saveAi(null, new AiForm(true, EAiProvider.ANTHROPIC, null, "claude-test"));
        var after = service.all(null);
        assertThat(after.slack().enabled()).isFalse();
        assertThat(after.slack().hasWebhookUrl()).isTrue();
        assertThat(after.telegram().chatId()).isEqualTo("-100999");
        assertThat(after.telegram().hasBotToken()).isTrue();
        assertThat(after.aiAssistant().provider()).isEqualTo(EAiProvider.ANTHROPIC);
        assertThat(after.aiAssistant().hasApiKey()).isTrue();
    }

    @Test
    void enablingNeedsTheSecretAndDeleteClears() {
        assertThatThrownBy(() -> service.saveSlack(null, new SlackForm(true, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_012));
        assertThatThrownBy(() -> service.saveGoogleCalendar(null, new GoogleCalendarForm(true, "c", "{\"type\":\"user\"}")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_016));
        service.saveSlack(null, new SlackForm(true, SLACK_URL));
        when(repo.findByCompanyIdAndSection(10L, "INT_SLACK")).thenReturn(Optional.of(rows.get("10:INT_SLACK")));
        service.delete(null, "slack");
        verify(repo).delete(rows.get("10:INT_SLACK"));
        assertThatThrownBy(() -> service.delete(null, "fax")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_018));
    }

    @Test
    void slackUrlMustBeOnTheSlackHost() {
        assertThatThrownBy(() -> service.saveSlack(null, new SlackForm(true, "https://evil.example.com/services/x")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_013));
        assertThatThrownBy(() -> service.saveSlack(null, new SlackForm(true, "http://hooks.slack.com/services/x")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_013));
    }

    @Test
    void testsPostOnlyToTheOfficialHostsAndReturnOnlyTheStatus() {
        when(http.postJson(any(), anyString(), anyMap())).thenReturn(new OutboundHttp.Result(true, 200, null));
        service.saveSlack(null, new SlackForm(true, SLACK_URL));
        service.saveTelegram(null, new TelegramForm(true, BOT_TOKEN, "-100123"));

        var slack = service.testSlack(null);
        var telegram = service.testTelegram(null);

        assertThat(slack.delivered()).isTrue();
        assertThat(telegram.statusCode()).isEqualTo(200);
        ArgumentCaptor<URI> uris = ArgumentCaptor.forClass(URI.class);
        verify(http, org.mockito.Mockito.times(2)).postJson(uris.capture(), anyString(), anyMap());
        assertThat(uris.getAllValues()).extracting(URI::getHost).containsExactly("hooks.slack.com", "api.telegram.org");
    }

    @Test
    void testWithoutConfigurationIsRefused() {
        assertThatThrownBy(() -> service.testSlack(null)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_017));
        verify(http, never()).postJson(any(), anyString(), anyMap());
    }

    @Test
    void anotherCompanysIntegrationsAreNotVisible() {
        service.saveSlack(null, new SlackForm(true, SLACK_URL));
        UserProfileDeleteTest.login(2L, 20L, "IAM_SETTINGS_MANAGE");
        // companyId is ignored for a company user: company 20 sees its own (empty) settings.
        assertThat(service.all(10L).slack().configured()).isFalse();
    }
}
