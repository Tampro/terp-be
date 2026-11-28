package com.terp.erp.store.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.terp.erp.exception.ApiException;
import com.terp.erp.store.common.EStoreError;
import com.terp.erp.store.config.StoreProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryClientTest {
    private HttpServer server;
    private InventoryClient client;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private volatile String body = "";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/inventory/warehouses/", ex -> {
            calls.incrementAndGet();
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new InventoryClient(new StoreProperties("http://unused", url, "http://unused"), new ObjectMapper());
        Jwt jwt = new Jwt("caller-token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", "kc-1"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeWarehouseOfTheCompany() {
        body = "{\"result\":true,\"data\":{\"id\":\"7\",\"name\":\"Store One\",\"active\":true}}";
        assertThat(client.activeWarehouse(7L).name()).isEqualTo("Store One");
    }

    @Test
    void inactiveWarehouseIsRefused() {
        body = "{\"result\":true,\"data\":{\"id\":\"7\",\"name\":\"Old\",\"active\":false}}";
        assertThatThrownBy(() -> client.activeWarehouse(7L))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EStoreError.STO_004));
    }

    @Test
    void unknownOrForeignWarehouseIsRefused() {
        status = 404;
        body = "{\"result\":false,\"code\":\"INV_007\",\"message\":\"The warehouse does not exist.\"}";
        assertThatThrownBy(() -> client.activeWarehouse(7L))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EStoreError.STO_004));
    }

    @Test
    void inventoryDownAnswers503AfterOneRetry() {
        status = 500;
        body = "{}";
        assertThatThrownBy(() -> client.activeWarehouse(7L)).satisfies(e -> {
            ApiException a = (ApiException) e;
            assertThat(a.getErrorCode()).isEqualTo(EStoreError.STO_006);
            assertThat(a.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        });
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void otherBusinessErrorsAreRelayed() {
        status = 403;
        body = "{\"result\":false,\"code\":\"COMMON_004\",\"message\":\"No permission\"}";
        assertThatThrownBy(() -> client.activeWarehouse(7L)).isInstanceOf(RemoteApiException.class)
                .satisfies(e -> assertThat(((RemoteApiException) e).getCode()).isEqualTo("COMMON_004"));
    }
}
