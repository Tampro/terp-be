package com.terp.erp.iam.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.integration.WebhookService.WebhookForm;
import com.terp.erp.iam.settings.ESettingsError;
import com.terp.erp.iam.settings.SecretCipher;
import com.terp.erp.iam.user.UserProfileDeleteTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookServiceTest {
    private static final String SECRET = "test-signing-secret-0123";

    private final CompanyWebhookRepository repo = mock(CompanyWebhookRepository.class);
    private final SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
    /** hooks.example.com is public, internal.example.com resolves to a private address. */
    private final UrlGuard guard = new UrlGuard(host -> new InetAddress[]{InetAddress.getByName(
            host.equals("internal.example.com") ? "192.168.0.10" : "93.184.216.34")});
    private final OutboundHttp http = mock(OutboundHttp.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC);
    private final WebhookService service = new WebhookService(repo, cipher, guard, http, new ObjectMapper(), clock);

    @BeforeEach
    void setUp() {
        UserProfileDeleteTest.login(1L, 10L, "IAM_SETTINGS_MANAGE");
        when(repo.save(any())).thenAnswer(i -> {
            CompanyWebhook w = i.getArgument(0);
            w.setId(5L);
            return w;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static WebhookForm form(String event, String url, String secret) {
        return new WebhookForm(event, url, null, true, secret, null);
    }

    @Test
    void createStoresTheSecretEncryptedAndNeverReturnsIt() throws Exception {
        var dto = service.create(99L, form("NEW_SALES_INVOICE", "https://hooks.example.com/erp", SECRET));

        ArgumentCaptor<CompanyWebhook> saved = ArgumentCaptor.forClass(CompanyWebhook.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getCompanyId()).isEqualTo(10L);
        assertThat(saved.getValue().getSecretEnc()).isNotBlank().doesNotContain(SECRET);
        assertThat(dto.hasSecret()).isTrue();
        assertThat(dto.module()).isEqualTo(EWebhookEvent.EModule.SALES);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(dto)).doesNotContain(SECRET);
    }

    @Test
    void formRules() {
        assertThatThrownBy(() -> service.create(null, form("NOPE", "https://hooks.example.com/erp", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_014));
        assertThatThrownBy(() -> service.create(null, form("NEW_LEAD", "http://hooks.example.com/erp", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_013));
        assertThatThrownBy(() -> service.create(null, form("NEW_LEAD", "https://127.0.0.1/erp", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_013));
        assertThatThrownBy(() -> service.create(null, form("NEW_LEAD", "https://hooks.example.com/erp", "short")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_019));
        verify(repo, never()).save(any());
    }

    @Test
    void anotherCompanysWebhookIsNotFound() {
        CompanyWebhook w = new CompanyWebhook();
        w.setId(7L);
        w.setCompanyId(20L);
        w.setEventCode(EWebhookEvent.NEW_LEAD);
        w.setUrl("https://hooks.example.com/erp");
        when(repo.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(w));
        for (Runnable r : new Runnable[]{() -> service.get(7L), () -> service.delete(7L), () -> service.test(7L),
                () -> service.update(7L, form("NEW_LEAD", "https://hooks.example.com/x", null))}) {
            assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_015);
                assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            });
        }
        assertThat(w.isDeleted()).isFalse();
        verify(http, never()).postJson(any(), anyString(), anyMap());
    }

    @Test
    void testSignsThePayloadAndReturnsOnlyTheStatus() {
        CompanyWebhook w = new CompanyWebhook();
        w.setId(7L);
        w.setCompanyId(10L);
        w.setEventCode(EWebhookEvent.NEW_CONTRACT);
        w.setUrl("https://hooks.example.com/erp");
        w.setSecretEnc(cipher.encrypt(SECRET));
        when(repo.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(w));
        when(http.postJson(any(), anyString(), anyMap())).thenReturn(new OutboundHttp.Result(false, 500, null));

        var r = service.test(7L);

        assertThat(r.delivered()).isFalse();
        assertThat(r.statusCode()).isEqualTo(500);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(http).postJson(any(), body.capture(), headers.capture());
        assertThat(body.getValue()).contains("\"event\":\"NEW_CONTRACT\"", "\"test\":true");
        String ts = String.valueOf(clock.instant().getEpochSecond());
        assertThat(headers.getValue()).containsEntry("X-ERP-Timestamp", ts)
                .containsEntry("X-ERP-Signature", "sha256=" + WebhookService.sign(SECRET, ts + "." + body.getValue()))
                .containsEntry("X-ERP-Event", "NEW_CONTRACT");
    }

    @Test
    void testRefusesAHostThatNowResolvesToAPrivateAddress() {
        CompanyWebhook w = new CompanyWebhook();
        w.setId(8L);
        w.setCompanyId(10L);
        w.setEventCode(EWebhookEvent.NEW_LEAD);
        w.setUrl("https://internal.example.com/erp");
        when(repo.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(w));
        assertThatThrownBy(() -> service.test(8L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_013));
        verify(http, never()).postJson(any(), anyString(), anyMap());
    }

    @Test
    void hmacMatchesAKnownVector() {
        // RFC 4231 test case 2.
        assertThat(WebhookService.sign("Jefe", "what do ya want for nothing?"))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }
}
