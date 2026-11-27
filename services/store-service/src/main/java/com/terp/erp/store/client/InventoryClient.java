package com.terp.erp.store.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.config.StoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** inventory-service as the caller: the warehouse a store sells from must be of the caller's company and active. */
@Component
public class InventoryClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Warehouse(String id, String name, boolean active) {
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    @Autowired
    public InventoryClient(StoreProperties props, ObjectMapper mapper) {
        this(new CallerHttp(props.inventoryServiceUrl(), mapper, EStoreError.STO_006, "inventory-service"), mapper);
    }

    InventoryClient(CallerHttp http, ObjectMapper mapper) {
        this.http = http;
        this.mapper = mapper;
    }

    /**
     * The active warehouse of the caller's company. Unknown or of another company (inventory answers 404 INV_007) or
     * inactive → 400 STO_004; any other business error of inventory (e.g. 403 without INVENTORY_VIEW) is relayed as is;
     * unreachable / 5xx → 503 STO_006. The GET is idempotent, so one 503 is retried once (spec NFR-2).
     */
    public Warehouse activeWarehouse(Long id) {
        JsonNode data;
        try {
            data = fetch(id);
        } catch (ApiException e) {
            if (e.getStatus() != HttpStatus.SERVICE_UNAVAILABLE) {
                throw e;
            }
            data = fetch(id);
        }
        Warehouse w = mapper.convertValue(data, Warehouse.class);
        if (w == null || !w.active()) {
            throw ApiException.badRequest(EStoreError.STO_004);
        }
        return w;
    }

    private JsonNode fetch(Long id) {
        try {
            return http.call(c -> c.get().uri("/api/v1/inventory/warehouses/{id}", id)).path("data");
        } catch (RemoteApiException e) {
            if (e.getStatus().value() == 404) {
                throw ApiException.badRequest(EStoreError.STO_004);
            }
            throw e;
        }
    }
}
