package com.terp.erp.member.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.common.Nulls;
import com.terp.erp.exception.ApiException;
import com.terp.erp.member.common.EMemberError;
import com.terp.erp.member.config.MemberProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The company of a shop slug, asked to store-service as member-service's service account. The company always comes
 * from here, never from the customer. Unknown / disabled shop or inactive company → 404 MEM_001; store-service down or
 * refusing the service account → 503 MEM_006, never a guess.
 */
@Slf4j
@Component
public class ShopClient {
    private final RestClient client;
    private final MemberProperties props;
    private final ServiceTokenProvider tokens;
    private final ObjectMapper mapper;

    @Autowired
    public ShopClient(MemberProperties props, ServiceTokenProvider tokens, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(5)));
        this.client = RestClient.builder().requestFactory(rf).build();
        this.props = props;
        this.tokens = tokens;
        this.mapper = mapper;
    }

    public long companyOf(String slug) {
        String url = props.storeServiceUrl() + "/api/v1/internal/store/shops/"
                + UriUtils.encodePathSegment(slug == null ? "" : slug, StandardCharsets.UTF_8);
        for (int attempt = 0; ; attempt++) {
            try {
                String body = client.get().uri(Nulls.nonNull(java.net.URI.create(url)))
                        .header("Authorization", "Bearer " + tokens.token())
                        .retrieve().body(String.class);
                JsonNode data = mapper.readTree(body == null ? "{}" : body).path("data");
                return Long.parseLong(data.path("companyId").asText());
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404) {
                    throw ApiException.notFound(EMemberError.MEM_001);
                }
                log.error("shop lookup refused: {}", e.getStatusCode().value());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EMemberError.MEM_006);
            } catch (RestClientException | java.io.IOException | NumberFormatException e) {
                if (attempt >= 1) { // one retry of the idempotent GET
                    log.error("shop lookup failed: {}", e.getClass().getSimpleName());
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EMemberError.MEM_006);
                }
            }
        }
    }
}
