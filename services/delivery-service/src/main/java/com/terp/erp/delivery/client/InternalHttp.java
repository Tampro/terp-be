package com.terp.erp.delivery.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.common.Nulls;
import com.terp.erp.delivery.common.EDeliveryError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;

/**
 * GET of an internal API with delivery-service's service token: connect 2 s / read 5 s, one retry (the reads are
 * idempotent). 404 → the caller's not-found code; anything else → 503 DLV_003, never a made-up value.
 */
@Slf4j
@Component
public class InternalHttp {
    private final RestClient client;
    private final ServiceTokenProvider tokens;

    public InternalHttp(ServiceTokenProvider tokens) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(5)));
        this.client = RestClient.builder().requestFactory(rf).build();
        this.tokens = tokens;
    }

    public JsonNode get(String url, ErrorCode notFound) {
        for (int attempt = 0; ; attempt++) {
            try {
                JsonNode body = client.get().uri(Nulls.nonNull(URI.create(url)))
                        .header("Authorization", "Bearer " + tokens.token()).retrieve().body(JsonNode.class);
                JsonNode data = body == null ? null : body.path("data");
                if (data == null || data.isMissingNode() || data.isNull()) {
                    throw new RestClientException("empty body");
                }
                return data;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404) {
                    throw ApiException.badRequest(notFound);
                }
                log.error("internal read refused: {}", e.getStatusCode().value());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EDeliveryError.DLV_003);
            } catch (RestClientException e) {
                if (attempt >= 1) {
                    log.error("internal read failed: {}", e.getClass().getSimpleName());
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EDeliveryError.DLV_003);
                }
            }
        }
    }
}
