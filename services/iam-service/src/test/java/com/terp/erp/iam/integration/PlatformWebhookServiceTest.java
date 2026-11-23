package com.terp.erp.iam.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.integration.PlatformWebhookService.PlatformWebhookForm;
import com.terp.erp.iam.settings.SecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformWebhookServiceTest {
    private static final String SECRET = "test-signing-secret-0123";

    private final PlatformWebhookRepository repo = mock(PlatformWebhookRepository.class);
    private final SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
    /** hooks.example.com is public, internal.example.com private, nowhere.invalid does not resolve. */
    private final UrlGuard guard = new UrlGuard(host -> {
        if (host.endsWith(".invalid")) {
            throw new UnknownHostException(host);
        }
        return new InetAddress[]{InetAddress.getByName(
                host.equals("internal.example.com") ? "192.168.0.10" : "93.184.216.34")};
    });
    private final OutboundHttp http = mock(OutboundHttp.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneOffset.UTC);
    private final PlatformWebhookService service = new PlatformWebhookService(repo, cipher, guard, http,
            new ObjectMapper(), clock);

    @BeforeEach
    void setUp() {
        when(repo.save(any())).thenAnswer(i -> {
            PlatformWebhook w = i.getArgument(0);
            w.setId(3L);
            return w;
        });
    }

    private static PlatformWebhookForm form(String event, String url, String secret) {
        return new PlatformWebhookForm(event, url, null, true, secret, null);
    }

    private static String code(Throwable t) {
        return ((ApiException) t).getErrorCode().name();
    }

    @Test
    void createKeepsTheSecretEncryptedAndNeverReturnsIt() throws Exception {
        var dto = service.create(form("NEW_COMPANY", "https://hooks.example.com/erp", SECRET));

        ArgumentCaptor<PlatformWebhook> saved = ArgumentCaptor.forClass(PlatformWebhook.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getSecretEnc()).isNotBlank().doesNotContain(SECRET);
        assertThat(saved.getValue().getMethod()).isEqualTo(CompanyWebhook.EMethod.POST);
        assertThat(dto.hasSecret()).isTrue();
        assertThat(dto.eventName()).isEqualTo("New Company");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(dto)).doesNotContain(SECRET);
    }

    @Test
    void formRulesAndSsrfRefusals() {
        assertThatThrownBy(() -> service.create(form("NEW_SALES_INVOICE", "https://hooks.example.com/erp", null)))
                .satisfies(t -> assertThat(code(t)).isEqualTo("SETTINGS_014"));
        assertThatThrownBy(() -> service.create(form("NEW_ORDER", "http://hooks.example.com/erp", null)))
                .satisfies(t -> assertThat(code(t)).isEqualTo("SETTINGS_013"));
        assertThatThrownBy(() -> service.create(form("NEW_ORDER", "https://127.0.0.1/erp", null)))
                .satisfies(t -> assertThat(code(t)).isEqualTo("SETTINGS_013"));
        assertThatThrownBy(() -> service.create(form("NEW_ORDER", "https://hooks.example.com/erp", "short")))
                .satisfies(t -> assertThat(code(t)).isEqualTo("SETTINGS_019"));
        verify(repo, never()).save(any());
    }

    @Test
    void testRefusesPrivateAndUnresolvableHostsBeforeAnyCall() {
        PlatformWebhook priv = new PlatformWebhook();
        priv.setId(4L);
        priv.setEventCode(EPlatformWebhookEvent.NEW_USER);
        priv.setUrl("https://internal.example.com/hook");
        PlatformWebhook gone = new PlatformWebhook();
        gone.setId(5L);
        gone.setEventCode(EPlatformWebhookEvent.NEW_USER);
        gone.setUrl("https://nowhere.invalid/hook");
        when(repo.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(priv));
        when(repo.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(gone));

        assertThatThrownBy(() -> service.test(4L)).satisfies(t -> assertThat(code(t)).isEqualTo("SETTINGS_013"));
        assertThatThrownBy(() -> service.test(5L)).satisfies(t -> assertThat(code(t)).isEqualTo("SETTINGS_013"));
        verify(http, never()).postJson(any(), any(), any());
    }

    @Test
    void testDeliveryIsSignedAndHasNoCompany() {
        PlatformWebhook w = new PlatformWebhook();
        w.setId(6L);
        w.setEventCode(EPlatformWebhookEvent.NEW_HELPDESK_TICKET);
        w.setUrl("https://hooks.example.com/erp");
        w.setSecretEnc(cipher.encrypt(SECRET));
        when(repo.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(w));
        when(http.postJson(any(), any(), any())).thenReturn(new com.terp.erp.net.OutboundHttp.Result(true, 200, null));

        service.test(6L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, String>> headers = ArgumentCaptor.forClass(java.util.Map.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(http).postJson(any(), body.capture(), headers.capture());
        assertThat(body.getValue()).contains("\"module\":\"PLATFORM\"").contains("\"companyId\":null");
        String ts = headers.getValue().get("X-ERP-Timestamp");
        assertThat(headers.getValue().get("X-ERP-Signature"))
                .isEqualTo("sha256=" + WebhookService.sign(SECRET, ts + "." + body.getValue()));
    }

    @Test
    void unknownIdIsNotFound() {
        when(repo.findByIdAndDeletedFalse(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(9L)).satisfies(t -> assertThat(code(t)).isEqualTo("SETTINGS_026"));
    }

    @Test
    void everyEndpointIsSuperAdminOnly() {
        // A company admin (IAM_SETTINGS_MANAGE but not super admin) is refused by isSuperAdmin(): 403.
        for (var m : PlatformWebhookController.class.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                assertThat(m.getAnnotation(PreAuthorize.class)).as(m.getName()).isNotNull();
                assertThat(m.getAnnotation(PreAuthorize.class).value()).as(m.getName())
                        .isEqualTo("@PermissionChecker.isSuperAdmin()");
            }
        }
    }

    @Test
    void catalogNamesTheRoutingKeys() {
        assertThat(service.events()).extracting(PlatformWebhookService.PlatformEventDto::eventType)
                .allMatch(t -> t.startsWith("platform."));
    }
}
