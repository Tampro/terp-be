package com.terp.erp.notification.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.common.Nulls;
import com.terp.erp.exception.ApiException;
import com.terp.erp.notification.common.ENotificationError;
import com.terp.erp.notification.config.OrderClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * order-service's internal notification facts with the service-account token (GET, so one retry is safe): connect 2 s,
 * read 5 s. 404 → empty (the order is gone: nothing to send); anything else → 503 NTF_004, a retryable send failure.
 */
@Slf4j
@Component
public class OrderInternalClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Facts(String orderNo, String storeName, String recipientName, String email, BigDecimal total,
                        String status, String deliveryStatus) {
    }

    private final RestClient client;
    private final ServiceTokenProvider tokens;
    private final ObjectMapper mapper;

    public OrderInternalClient(OrderClientProperties props, ServiceTokenProvider tokens, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(5)));
        this.client = RestClient.builder().baseUrl(Nulls.nonNull(props.serviceUrl())).requestFactory(rf).build();
        this.tokens = tokens;
        this.mapper = mapper;
    }

    public Optional<Facts> facts(Long companyId, Long orderId) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode body = client.get().uri("/api/v1/internal/order/orders/{id}/notification?companyId={c}",
                                orderId, companyId)
                        .header("Authorization", "Bearer " + tokens.token())
                        .retrieve().body(JsonNode.class);
                JsonNode data = body == null ? null : body.path("data");
                return data == null || data.isMissingNode() || data.isNull() ? Optional.empty()
                        : Optional.of(mapper.convertValue(data, Facts.class));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (HttpClientErrorException e) {
                log.error("order-service internal call refused: HTTP {}", e.getStatusCode().value());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ENotificationError.NTF_004);
            } catch (RestClientException e) {
                last = e;
            }
        }
        log.warn("order-service internal call failed: {}", last.getClass().getSimpleName());
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ENotificationError.NTF_004);
    }
}
