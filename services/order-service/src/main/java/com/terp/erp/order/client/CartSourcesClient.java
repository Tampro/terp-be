package com.terp.erp.order.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.common.Nulls;
import com.terp.erp.exception.ApiException;
import com.terp.erp.order.common.EOrderError;
import com.terp.erp.order.config.OrderProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * What a cart needs from the other services, as order-service's service account (/api/v1/internal/**): the caller's
 * membership (member-service), the store's fees and minimums (store-service), live prices (inventory-service).
 * A not-found answers the cart's own code; any other failure is 503 ORD_007 — a cart total is never guessed.
 */
@Slf4j
@Component
public class CartSourcesClient {
    private final RestClient client;
    private final OrderProperties props;
    private final ServiceTokenProvider tokens;
    private final ObjectMapper mapper;

    public record Membership(long customerId, long companyId) {
    }

    public record StoreView(String id, String name, long warehouseId, BigDecimal deliveryFee, BigDecimal freeDeliveryThreshold,
                            BigDecimal minOrderAmount, BigDecimal firstOrderMinAmount, BigDecimal bagFee,
                            boolean orderableNow, Instant opensAt) {
    }

    public record ShelfItem(String itemId, String name, String sku, BigDecimal salePrice, Integer purchaseLimitQty,
                            boolean soldOut) {
    }

    @Autowired
    public CartSourcesClient(OrderProperties props, ServiceTokenProvider tokens, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(5)));
        this.client = RestClient.builder().requestFactory(rf).build();
        this.props = props;
        this.tokens = tokens;
        this.mapper = mapper;
    }

    /** 404 (shop unavailable / not a member) → ORD_001. */
    public Membership membership(String slug, String sub) {
        String url = UriComponentsBuilder.fromUriString(props.memberServiceUrl() + "/api/v1/internal/member/shops/{slug}/members/{sub}")
                .buildAndExpand(slug, sub).encode().toUriString();
        JsonNode d = get(url, EOrderError.ORD_001);
        return new Membership(d.path("customerId").asLong(), d.path("companyId").asLong());
    }

    /** 404 (store not of the shop, inactive, shop unavailable) → ORD_005. */
    public StoreView store(String slug, long storeId) {
        String url = UriComponentsBuilder.fromUriString(props.storeServiceUrl() + "/api/v1/internal/store/shops/{slug}/stores/{id}")
                .buildAndExpand(slug, storeId).encode().toUriString();
        JsonNode d = get(url, EOrderError.ORD_005);
        try {
            return mapper.treeToValue(d, StoreView.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EOrderError.ORD_007);
        }
    }

    /** Live shelf data of these items at the warehouse; items not on sale are simply absent. One call. */
    public List<ShelfItem> shelf(long companyId, long warehouseId, Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        String url = UriComponentsBuilder.fromUriString(props.inventoryServiceUrl()
                        + "/api/v1/internal/inventory/warehouses/{wid}/shelf/items")
                .queryParam("companyId", companyId)
                .queryParam("ids", itemIds.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .buildAndExpand(warehouseId).encode().toUriString();
        JsonNode d = get(url, EOrderError.ORD_007);
        try {
            return mapper.convertValue(d, new TypeReference<List<ShelfItem>>() { });
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EOrderError.ORD_007);
        }
    }

    public record Address(String recipientName, String phone, String postcode, String address1, String address2,
                          BigDecimal lat, BigDecimal lon, String deliveryNote) {
    }

    public record StockLine(long itemId, long warehouseId, BigDecimal quantity) {
    }

    /** The member's own delivery address with the plain phone; 404 (not theirs / unknown) → ORD_012. */
    public Address address(String slug, String sub, long addressId) {
        String url = UriComponentsBuilder.fromUriString(props.memberServiceUrl()
                        + "/api/v1/internal/member/shops/{slug}/members/{sub}/addresses/{id}")
                .buildAndExpand(slug, sub, addressId).encode().toUriString();
        return mapper.convertValue(get(url, EOrderError.ORD_012), Address.class);
    }

    /** Whether an active zone of this store contains the point. */
    public boolean serves(String slug, long storeId, BigDecimal lat, BigDecimal lon) {
        String url = UriComponentsBuilder.fromUriString(props.storeServiceUrl() + "/api/v1/internal/store/shops/{slug}/stores/{id}/serves")
                .queryParam("lat", lat.toPlainString()).queryParam("lon", lon.toPlainString())
                .buildAndExpand(slug, storeId).encode().toUriString();
        return get(url, EOrderError.ORD_005).path("serves").asBoolean(false);
    }

    /**
     * Moves the order's stock out once (inventory is idempotent on ORDER + order id, so a retry after a timeout is safe).
     * Not enough stock (400 INV_017) → 400 ORD_015; anything else → 503 ORD_007.
     */
    public void moveStock(long companyId, long orderId, List<StockLine> lines) {
        String url = UriComponentsBuilder.fromUriString(props.inventoryServiceUrl() + "/api/v1/internal/inventory/stock/moves")
                .queryParam("companyId", companyId).toUriString();
        java.util.Map<String, Object> body = java.util.Map.of("sourceType", "ORDER", "sourceId", orderId, "lines", lines);
        for (int attempt = 0; ; attempt++) {
            try {
                client.post().uri(Nulls.nonNull(java.net.URI.create(url)))
                        .header("Authorization", "Bearer " + tokens.token())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(mapper.writeValueAsString(body)).retrieve().toBodilessEntity();
                return;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 400) {
                    throw ApiException.badRequest(EOrderError.ORD_015);
                }
                log.error("stock move refused: {}", e.getStatusCode().value());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EOrderError.ORD_007);
            } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
                if (attempt >= 1) {
                    log.error("stock move failed: {}", e.getClass().getSimpleName());
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EOrderError.ORD_007);
                }
            }
        }
    }

    private JsonNode get(String url, EOrderError notFound) {
        for (int attempt = 0; ; attempt++) {
            try {
                String body = client.get().uri(Nulls.nonNull(java.net.URI.create(url)))
                        .header("Authorization", "Bearer " + tokens.token())
                        .retrieve().body(String.class);
                return mapper.readTree(body == null ? "{}" : body).path("data");
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 404 && notFound != EOrderError.ORD_007) {
                    throw ApiException.notFound(notFound);
                }
                log.error("cart source refused: {}", e.getStatusCode().value());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EOrderError.ORD_007);
            } catch (RestClientException | java.io.IOException e) {
                if (attempt >= 1) { // one retry of the idempotent GET
                    log.error("cart source failed: {}", e.getClass().getSimpleName());
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EOrderError.ORD_007);
                }
            }
        }
    }
}
