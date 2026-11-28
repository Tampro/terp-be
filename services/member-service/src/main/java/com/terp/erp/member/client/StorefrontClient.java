package com.terp.erp.member.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.common.Nulls;
import com.terp.erp.member.config.MemberProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The store serving an address, from store-service's public storefront resolve (no token). Informational: no store
 * delivering there, or store-service failing, gives empty and never blocks saving the address.
 */
@Slf4j
@Component
public class StorefrontClient {
    private final RestClient client;
    private final MemberProperties props;
    private final ObjectMapper mapper;

    public record ServingStore(String id, String name, boolean orderableNow, Instant opensAt) {
    }

    @Autowired
    public StorefrontClient(MemberProperties props, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(3)));
        this.client = RestClient.builder().requestFactory(rf).build();
        this.props = props;
        this.mapper = mapper;
    }

    public Optional<ServingStore> servingStore(String slug, BigDecimal lat, BigDecimal lon) {
        String url = UriComponentsBuilder.fromUriString(props.storeServiceUrl() + "/api/v1/public/shop/{slug}/resolve")
                .queryParam("lat", lat.toPlainString()).queryParam("lon", lon.toPlainString())
                .buildAndExpand(slug).encode().toUriString();
        try {
            String body = client.get().uri(Nulls.nonNull(java.net.URI.create(url))).retrieve().body(String.class);
            JsonNode d = mapper.readTree(body == null ? "{}" : body).path("data");
            JsonNode store = d.path("store");
            if (store.isMissingNode() || store.isNull()) {
                return Optional.empty();
            }
            Instant opens = d.path("opensAt").isTextual() ? Instant.parse(d.path("opensAt").asText()) : null;
            return Optional.of(new ServingStore(store.path("id").asText(null), store.path("name").asText(null),
                    d.path("orderableNow").asBoolean(false), opens));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return Optional.empty(); // STO_010: no store delivers there
        } catch (Exception e) {
            log.warn("serving store unavailable: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
