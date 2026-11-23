package com.terp.erp.iam.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.integration.CompanyWebhook.EMethod;
import com.terp.erp.iam.integration.IntegrationDtos.TestResult;
import com.terp.erp.iam.settings.ESettingsError;
import com.terp.erp.iam.settings.SecretCipher;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform webhooks of the super admin (ERP manual 3.7). Same delivery contract as company webhooks (WebhookService):
 * POST of JSON {event, eventName, module: "PLATFORM", eventId, companyId: null, occurredAt, data}, signed with
 * X-ERP-Timestamp / X-ERP-Signature when a secret is set, same SSRF rules (UrlGuard). Real deliveries are
 * notification-service's (platform.* events, companyId null). Callers are super admins only (controller).
 */
@Service
@RequiredArgsConstructor
public class PlatformWebhookService {
    public static final String MODULE = "PLATFORM";

    public record PlatformEventDto(String code, String name, String eventType) {
    }

    public record PlatformWebhookDto(String id, String eventCode, String eventName, String url, EMethod method,
                                     boolean active, boolean hasSecret, Instant createdDate, Instant updatedDate) {
    }

    public record PlatformWebhookForm(
            @Schema(description = "Code of GET /settings/admin/webhooks/events", example = "NEW_COMPANY",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 60) String eventCode,
            @Schema(description = "https only, public host, port 443/8443", example = "https://hooks.example.com/erp",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 1000) String url,
            @Schema(description = "POST only; null = POST. The reference also offers GET: not supported",
                    example = "POST") EMethod method,
            @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean active,
            @Schema(description = "Write-only HMAC signing secret, 16-200 chars. Update: blank/omitted keeps the "
                    + "stored one", example = "change-me-to-a-long-random-secret") @Size(max = 200) String secret,
            @Schema(description = "Update only: true removes the signing secret") Boolean removeSecret) {
    }

    private final PlatformWebhookRepository repo;
    private final SecretCipher cipher;
    private final UrlGuard guard;
    private final OutboundHttp http;
    private final ObjectMapper mapper;
    private final Clock clock;

    public List<PlatformEventDto> events() {
        return Arrays.stream(EPlatformWebhookEvent.values())
                .map(e -> new PlatformEventDto(e.name(), e.getDisplayName(), e.getEventType())).toList();
    }

    @Transactional(readOnly = true)
    public Page<PlatformWebhookDto> list(String eventCode, Pageable pageable) {
        Page<PlatformWebhook> page = eventCode == null || eventCode.isBlank() ? repo.findByDeletedFalse(pageable)
                : repo.findByEventCodeAndDeletedFalse(event(eventCode), pageable);
        return page.map(PlatformWebhookService::dto);
    }

    @Transactional(readOnly = true)
    public PlatformWebhookDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public PlatformWebhookDto create(PlatformWebhookForm f) {
        PlatformWebhook w = new PlatformWebhook();
        apply(w, f);
        return dto(repo.save(w));
    }

    /** Full replacement, except the secret: blank keeps it, removeSecret=true clears it. */
    @Transactional
    public PlatformWebhookDto update(Long id, PlatformWebhookForm f) {
        PlatformWebhook w = require(id);
        if (Boolean.TRUE.equals(f.removeSecret())) {
            w.setSecretEnc(null);
        }
        apply(w, f);
        return dto(w);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    /** One sample delivery (test=true), active or not; the answer's body is never read. */
    @Transactional(readOnly = true)
    public TestResult test(Long id) {
        PlatformWebhook w = require(id);
        var uri = guard.check(w.getUrl(), null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", w.getEventCode().name());
        body.put("eventName", w.getEventCode().getDisplayName());
        body.put("module", MODULE);
        body.put("test", true);
        body.put("companyId", null);
        body.put("sentAt", Instant.now(clock).toString());
        body.put("data", Map.of("message", "ERP test delivery"));
        String json;
        try {
            json = mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("not serializable", e);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-ERP-Event", w.getEventCode().name());
        if (w.getSecretEnc() != null) {
            String ts = String.valueOf(Instant.now(clock).getEpochSecond());
            headers.put("X-ERP-Timestamp", ts);
            headers.put("X-ERP-Signature", "sha256=" + WebhookService.sign(cipher.decrypt(w.getSecretEnc()),
                    ts + "." + json));
        }
        return TestResult.of(http.postJson(uri, json, headers));
    }

    /** Active webhooks of one event, for notification-service (the caller decrypts the secrets). */
    @Transactional(readOnly = true)
    public List<PlatformWebhook> active(EPlatformWebhookEvent e) {
        return repo.findByEventCodeAndActiveTrueAndDeletedFalseOrderByIdAsc(e);
    }

    public static EPlatformWebhookEvent event(String code) {
        return Arrays.stream(EPlatformWebhookEvent.values()).filter(e -> e.name().equals(code)).findFirst()
                .orElseThrow(() -> ApiException.badRequest(ESettingsError.SETTINGS_014, code));
    }

    // ----

    private void apply(PlatformWebhook w, PlatformWebhookForm f) {
        w.setEventCode(event(f.eventCode()));
        w.setUrl(guard.checkSyntax(f.url(), null).toString());
        w.setMethod(EMethod.POST);
        w.setActive(f.active());
        String secret = f.secret() == null || f.secret().isBlank() ? null : f.secret().trim();
        if (secret != null) {
            if (secret.length() < 16) {
                throw ApiException.badRequest(ESettingsError.SETTINGS_019, "secret");
            }
            w.setSecretEnc(cipher.encrypt(secret));
        }
    }

    private PlatformWebhook require(Long id) {
        return repo.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ESettingsError.SETTINGS_026));
    }

    private static PlatformWebhookDto dto(PlatformWebhook w) {
        return new PlatformWebhookDto(String.valueOf(w.getId()), w.getEventCode().name(),
                w.getEventCode().getDisplayName(), w.getUrl(), w.getMethod(), w.isActive(), w.getSecretEnc() != null,
                w.getCreatedDate(), w.getUpdatedDate());
    }
}
