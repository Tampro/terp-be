package com.terp.erp.commerce.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.config.CommerceProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** inventory-service as the caller: item data for document lines, and stock moves when an invoice or return posts. */
@Component
public class InventoryClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, String type, String name, String sku, String taxName, BigDecimal taxRate,
                       BigDecimal salePrice) {
        public boolean stocked() {
            return !"SERVICE".equals(type);
        }
    }

    public record MoveLine(Long itemId, Long warehouseId, BigDecimal quantity) {
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public InventoryClient(CommerceProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.inventoryServiceUrl(), mapper, ECommerceError.SAL_015, "inventory-service");
        this.mapper = mapper;
    }

    /** The item of the caller's company; inventory-service answers its own 404 otherwise (relayed). */
    public Item item(Long id) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/inventory/items/{id}", id)).path("data");
        return mapper.convertValue(data, Item.class);
    }

    /**
     * All-or-nothing stock change, idempotent on (sourceType, sourceId): a retry after a failure applies nothing
     * twice. Not enough stock comes back as inventory-service's own 400 (relayed).
     */
    public void move(String sourceType, Long sourceId, List<MoveLine> lines) {
        if (lines.isEmpty()) {
            return;
        }
        http.call(c -> c.post().uri("/api/v1/inventory/stock/moves")
                .body(Map.of("sourceType", sourceType, "sourceId", sourceId, "lines", lines)));
    }
}
