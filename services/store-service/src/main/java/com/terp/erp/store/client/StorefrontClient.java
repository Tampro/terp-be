package com.terp.erp.store.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.common.Nulls;
import com.terp.erp.exception.ApiException;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.config.StoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.function.Function;

/**
 * The storefront's reads from inventory-service and IAM, as store-service's service account (/api/v1/internal/**).
 * The company always comes from the shop slug, never from the customer. Reads are retried once on IO/5xx; a failure
 * is a 503 (STO_006 inventory, STO_018 IAM), never an empty page.
 */
@Slf4j
@Component
public class StorefrontClient {
    private final RestClient client;
    private final StoreProperties props;
    private final ServiceTokenProvider tokens;
    private final ObjectMapper mapper;

    public StorefrontClient(StoreProperties props, ServiceTokenProvider tokens, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(5)));
        this.client = RestClient.builder().requestFactory(rf).build();
        this.props = props;
        this.tokens = tokens;
        this.mapper = mapper;
    }

    /** IAM: exists, not deleted, ACTIVE. */
    public boolean companyActive(long companyId) {
        JsonNode data = get(props.iamServiceUrl() + "/api/v1/internal/companies/" + companyId + "/status",
                EStoreError.STO_018);
        return data.path("active").asBoolean(false);
    }

    public JsonNode categories(long companyId) {
        return get(UriComponentsBuilder.fromUriString(props.inventoryServiceUrl() + "/api/v1/internal/inventory/categories")
                .queryParam("companyId", companyId).toUriString(), EStoreError.STO_006);
    }

    /** One page (ResponseListDto JSON) of the warehouse's ON_SALE items. */
    public JsonNode shelf(long companyId, long warehouseId, Long categoryId, String keyword, int page, int size) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(props.inventoryServiceUrl()
                        + "/api/v1/internal/inventory/warehouses/" + warehouseId + "/shelf")
                .queryParam("companyId", companyId).queryParam("page", page).queryParam("size", size);
        if (categoryId != null) {
            b.queryParam("categoryId", categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            b.queryParam("keyword", keyword);
        }
        return get(b.encode().toUriString(), EStoreError.STO_006);
    }

    /** 404 from inventory (not on sale, other company) → 404 STO_017. */
    public JsonNode item(long companyId, long warehouseId, long itemId) {
        return get(UriComponentsBuilder.fromUriString(props.inventoryServiceUrl() + "/api/v1/internal/inventory/warehouses/"
                + warehouseId + "/shelf/" + itemId).queryParam("companyId", companyId).toUriString(), EStoreError.STO_006,
                e -> e.getStatusCode().value() == 404 ? ApiException.notFound(EStoreError.STO_017) : null);
    }

    private JsonNode get(String url, EStoreError unavailable) {
        return get(url, unavailable, e -> null);
    }

    private JsonNode get(String url, EStoreError unavailable, Function<HttpClientErrorException, ApiException> on4xx) {
        RestClientException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String body = client.get().uri(Nulls.nonNull(java.net.URI.create(url)))
                        .header("Authorization", "Bearer " + tokens.token())
                        .retrieve().body(String.class);
                return mapper.readTree(body == null ? "{}" : body).path("data");
            } catch (HttpClientErrorException e) {
                ApiException mapped = on4xx.apply(e);
                if (mapped != null) {
                    throw mapped;
                }
                // 401/403 = misconfigured service account: report it, do not pretend the shop is empty
                log.error("storefront internal call refused: {} {}", e.getStatusCode().value(), unavailable);
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, unavailable);
            } catch (RestClientException e) {
                last = e;
            } catch (java.io.IOException e) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, unavailable);
            }
        }
        log.error("storefront internal call failed: {} {}", last.getClass().getSimpleName(), unavailable);
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, unavailable);
    }
}
