package com.terp.erp.notification.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.terp.erp.common.Nulls;
import com.terp.erp.exception.ApiException;
import com.terp.erp.notification.common.ENotificationError;
import com.terp.erp.notification.config.NotificationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * IAM's internal endpoints with the service-account token (GET only, so one retry is safe). Answers hold decrypted
 * secrets: never log a body. IAM unreachable = NTF_004 (503), so the broker message or the delivery is retried.
 */
@Slf4j
@Component
public class IamInternalClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Smtp(String host, Integer port, String username, String password, String encryption,
                       String fromAddress, String fromName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Slack(String webhookUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Telegram(String botToken, String chatId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Webhook(String id, String url, String secret) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotificationConfig(String companyId, String defaultLanguage, boolean emailEnabled, Smtp smtp,
                                     Slack slack, Telegram telegram, List<Webhook> webhooks) {
        public List<Webhook> webhooks() {
            return webhooks == null ? List.of() : webhooks;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(String id, String fullName, String email, String companyId, boolean active) {
    }

    private final RestClient client;
    private final ServiceTokenProvider tokens;
    private final ObjectMapper mapper;

    public IamInternalClient(NotificationProperties props, ServiceTokenProvider tokens, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(5)));
        this.client = RestClient.builder().baseUrl(Nulls.nonNull(props.iamServiceUrl())).requestFactory(rf).build();
        this.tokens = tokens;
        this.mapper = mapper;
    }

    /** eventCode / webhookEvent may be null. */
    public NotificationConfig config(Long companyId, String eventCode, String webhookEvent) {
        JsonNode data = get("/api/v1/internal/notification-config?companyId={c}&eventCode={e}&webhookEvent={w}",
                companyId, eventCode == null ? "" : eventCode, webhookEvent == null ? "" : webhookEvent)
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ENotificationError.NTF_004));
        return mapper.convertValue(data, NotificationConfig.class);
    }

    /** Empty when IAM does not know the user (404). */
    public Optional<Contact> contact(Long userId) {
        return get("/api/v1/internal/users/{id}/contact", userId).map(d -> mapper.convertValue(d, Contact.class));
    }

    private Optional<JsonNode> get(String uri, Object... vars) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode body = client.get().uri(uri, vars)
                        .header("Authorization", "Bearer " + tokens.token())
                        .retrieve().body(JsonNode.class);
                return Optional.ofNullable(body == null ? null : body.path("data"));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (HttpClientErrorException e) {
                // 400 / 401 / 403 will not get better by retrying: a configuration problem.
                log.error("IAM internal call refused: HTTP {}", e.getStatusCode().value());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ENotificationError.NTF_004);
            } catch (RestClientException e) {
                last = e;
            }
        }
        log.warn("IAM internal call failed: {}", last.getClass().getSimpleName());
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ENotificationError.NTF_004);
    }
}
