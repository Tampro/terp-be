package com.terp.erp.member.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.terp.erp.exception.ApiException;
import com.terp.erp.member.common.EMemberError;
import com.terp.erp.member.config.MemberProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopClientTest {
    private HttpServer server;
    private ShopClient client;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String body = "";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/internal/store/shops/", ex -> {
            calls.add(ex.getRequestURI().getPath() + " " + ex.getRequestHeaders().getFirst("Authorization"));
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);
        when(tokens.token()).thenReturn("svc");
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new ShopClient(new MemberProperties("http://unused", url, "http://unused", "http://unused"), tokens,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void companyOfAnOpenShopWithTheServiceToken() {
        body = "{\"result\":true,\"data\":{\"companyId\":\"10\",\"slug\":\"express\"}}";
        assertThat(client.companyOf("express")).isEqualTo(10L);
        assertThat(calls).containsExactly("/api/v1/internal/store/shops/express Bearer svc");
    }

    @Test
    void unknownShopIsMem001() {
        status = 404;
        body = "{\"result\":false,\"code\":\"STO_016\"}";
        assertThatThrownBy(() -> client.companyOf("nope"))
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(EMemberError.MEM_001));
    }

    @Test
    void storeDownIsMem006AfterOneRetry() {
        status = 500;
        body = "{}";
        assertThatThrownBy(() -> client.companyOf("express")).satisfies(e -> {
            assertThat(((ApiException) e).getErrorCode()).isEqualTo(EMemberError.MEM_006);
            assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        });
        assertThat(calls).hasSize(2);
    }
}
