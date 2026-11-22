package com.terp.erp.iam.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.integration.CompanyWebhook.EMethod;
import com.terp.erp.iam.integration.EWebhookEvent.EModule;
import com.terp.erp.iam.integration.IntegrationDtos.TestResult;
import com.terp.erp.iam.settings.ESettingsError;
import com.terp.erp.iam.settings.SecretCipher;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Company webhooks (ERP manual 19; reference packages/workdo/Webhook). A delivery is a POST of JSON
 * {event, eventName, module, test, companyId, sentAt, data}. With a signing secret it carries
 * {@code X-ERP-Timestamp: <epoch seconds>} and
 * {@code X-ERP-Signature: sha256=<hex HMAC-SHA256(secret, timestamp + "." + body)>} (the reference signs nothing:
 * Proposal). Only the test delivery exists today; TODO: real dispatch needs a broker.
 */
@Service
@RequiredArgsConstructor
public class WebhookService {
    public record EventDto(String code, String name, EModule module) {
    }

    public record EventGroup(EModule module, List<EventDto> events) {
    }

    public record WebhookDto(String id, String eventCode, String eventName, EModule module, String url, EMethod method,
                             boolean active, boolean hasSecret, Instant createdDate, Instant updatedDate) {
    }

    public record WebhookForm(
            @Schema(description = "Code of GET /settings/webhooks/events", example = "NEW_SALES_INVOICE",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 60) String eventCode,
            @Schema(description = "https only, public host, port 443/8443", example = "https://hooks.example.com/erp",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 1000) String url,
            @Schema(description = "POST only; null = POST", example = "POST") EMethod method,
            @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean active,
            @Schema(description = "Write-only HMAC signing secret, 16-200 chars. Update: blank/omitted keeps the "
                    + "stored one", example = "change-me-to-a-long-random-secret") @Size(max = 200) String secret,
            @Schema(description = "Update only: true removes the signing secret") Boolean removeSecret) {
    }

    private final CompanyWebhookRepository repo;
    private final SecretCipher cipher;
    private final UrlGuard guard;
    private final OutboundHttp http;
    private final ObjectMapper mapper;
    private final Clock clock;

    public List<EventGroup> events() {
        return Arrays.stream(EModule.values())
                .map(m -> new EventGroup(m, Arrays.stream(EWebhookEvent.values()).filter(e -> e.getModule() == m)
                        .map(e -> new EventDto(e.name(), e.getDisplayName(), m)).toList()))
                .filter(g -> !g.events().isEmpty()).toList();
    }

    @Transactional(readOnly = true)
    public Page<WebhookDto> list(Long requestedCompanyId, String eventCode, Pageable pageable) {
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        Page<CompanyWebhook> page = eventCode == null || eventCode.isBlank()
                ? repo.findByCompanyIdAndDeletedFalse(cid, pageable)
                : repo.findByCompanyIdAndEventCodeAndDeletedFalse(cid, event(eventCode), pageable);
        return page.map(WebhookService::dto);
    }

    @Transactional(readOnly = true)
    public WebhookDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public WebhookDto create(Long requestedCompanyId, WebhookForm f) {
        CompanyWebhook w = new CompanyWebhook();
        w.setCompanyId(TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003));
        apply(w, f);
        return dto(repo.save(w));
    }

    /** Full replacement, except the secret: blank keeps it, removeSecret=true clears it. */
    @Transactional
    public WebhookDto update(Long id, WebhookForm f) {
        CompanyWebhook w = require(id);
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
        CompanyWebhook w = require(id);
        var uri = guard.check(w.getUrl(), null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", w.getEventCode().name());
        body.put("eventName", w.getEventCode().getDisplayName());
        body.put("module", w.getEventCode().getModule().name());
        body.put("test", true);
        body.put("companyId", String.valueOf(w.getCompanyId()));
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
            headers.put("X-ERP-Signature", "sha256=" + sign(cipher.decrypt(w.getSecretEnc()), ts + "." + json));
        }
        return TestResult.of(http.postJson(uri, json, headers));
    }

    static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    // ----

    private void apply(CompanyWebhook w, WebhookForm f) {
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

    private static EWebhookEvent event(String code) {
        return Arrays.stream(EWebhookEvent.values()).filter(e -> e.name().equals(code)).findFirst()
                .orElseThrow(() -> ApiException.badRequest(ESettingsError.SETTINGS_014, code));
    }

    private CompanyWebhook require(Long id) {
        CompanyWebhook w = repo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(ESettingsError.SETTINGS_015));
        TenantGuard.assertSameCompany(w.getCompanyId(), ESettingsError.SETTINGS_015);
        return w;
    }

    private static WebhookDto dto(CompanyWebhook w) {
        return new WebhookDto(String.valueOf(w.getId()), w.getEventCode().name(), w.getEventCode().getDisplayName(),
                w.getEventCode().getModule(), w.getUrl(), w.getMethod(), w.isActive(), w.getSecretEnc() != null,
                w.getCreatedDate(), w.getUpdatedDate());
    }
}
