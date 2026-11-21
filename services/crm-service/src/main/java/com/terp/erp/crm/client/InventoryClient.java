package com.terp.erp.crm.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.config.CrmProperties;

import java.math.BigDecimal;

/** inventory-service as the caller: products linked to a lead or deal. */
@Component
public class InventoryClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, String type, String name, String sku, BigDecimal salePrice) {
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public InventoryClient(CrmProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.inventoryServiceUrl(), mapper, ECrmError.CRM_017, "inventory-service");
        this.mapper = mapper;
    }

    /** The item of the caller's company; inventory-service answers its own 404 otherwise (relayed). */
    public Item item(Long id) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/inventory/items/{id}", id)).path("data");
        return mapper.convertValue(data, Item.class);
    }
}
