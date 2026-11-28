package com.terp.erp.order.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.common.Nulls;
import com.terp.erp.exception.ApiException;
import com.terp.erp.order.common.EOrderError;
import com.terp.erp.order.config.ServiceAccountProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Access token of order-service's own service account (client credentials), cached until shortly before it expires
 * (notification-service's pattern). Never logged. A failure is a 503 ORD_007, never a fallback.
 */
@Slf4j
@Component
public class ServiceTokenProvider {
    private static final Duration MARGIN = Duration.ofSeconds(30);

    private final RestClient client;
    private final ServiceAccountProperties props;
    private final Clock clock;
    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public ServiceTokenProvider(ServiceAccountProperties props, Clock clock) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(5)));
        this.client = RestClient.builder().requestFactory(rf).build();
        this.props = props;
        this.clock = clock;
    }

    public synchronized String token() {
        if (token != null && clock.instant().isBefore(expiresAt.minus(MARGIN))) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        try {
            JsonNode body = client.post().uri(Nulls.nonNull(props.tokenUrl()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class);
            String value = body == null ? "" : body.path("access_token").asText("");
            if (value.isEmpty()) {
                throw new RestClientException("no access_token");
            }
            token = value;
            expiresAt = clock.instant().plusSeconds(body.path("expires_in").asLong(60));
            return token;
        } catch (RestClientException e) {
            log.error("service account token failed: {}", e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EOrderError.ORD_007);
        }
    }
}
