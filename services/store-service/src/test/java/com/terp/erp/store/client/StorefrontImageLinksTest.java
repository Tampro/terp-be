package com.terp.erp.store.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.terp.erp.store.config.StoreProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** One batch call per response; media failing gives no images instead of an error. */
class StorefrontImageLinksTest {
    private HttpServer server;
    private StorefrontClient client;
    private final List<String> queries = new CopyOnWriteArrayList<>();
    private final List<String> auths = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String body = "";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/internal/media/files", ex -> {
            queries.add(ex.getRequestURI().getRawQuery());
            auths.add(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);
        when(tokens.token()).thenReturn("svc-token");
        client = new StorefrontClient(new StoreProperties("http://unused", "http://unused", url), tokens, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void oneCallWithEveryIdAndTheCompany() {
        body = "{\"result\":true,\"data\":[{\"id\":\"31\",\"viewUrl\":\"http://g/31?sig=a\",\"linkExpiresAt\":2000},"
                + "{\"id\":\"32\",\"viewUrl\":\"http://g/32?sig=b\",\"linkExpiresAt\":1500}]}";
        StorefrontClient.ImageLinks links = client.imageLinks(10L, new java.util.LinkedHashSet<>(List.of(31L, 32L)));
        assertThat(queries).hasSize(1);
        assertThat(queries.get(0)).contains("ids=31,32", "companyId=10");
        assertThat(auths.get(0)).isEqualTo("Bearer svc-token");
        assertThat(links.urls()).containsEntry(31L, "http://g/31?sig=a").containsEntry(32L, "http://g/32?sig=b");
        assertThat(links.expiresAt()).isEqualTo(1_500_000L); // seconds from media → millis
    }

    @Test
    void noIdsMeansNoCall() {
        assertThat(client.imageLinks(10L, List.of()).urls()).isEmpty();
        assertThat(queries).isEmpty();
    }

    @Test
    void mediaFailingGivesNoImages() {
        status = 500;
        body = "{}";
        StorefrontClient.ImageLinks links = client.imageLinks(10L, List.of(31L));
        assertThat(links.urls()).isEmpty();
        assertThat(links.expiresAt()).isNull();
    }
}
