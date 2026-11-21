package com.terp.erp.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpGlobalFilterTest {
    @Test
    void replacesAClientSentAddressWithTheConnection() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login")
                .header("X-Client-IP", "6.6.6.6").header("X-Forwarded-For", "6.6.6.6")
                .remoteAddress(new InetSocketAddress("203.0.113.7", 50000)));
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        GatewayFilterChain chain = e -> {
            seen.set(e);
            return Mono.empty();
        };
        new ClientIpGlobalFilter().filter(exchange, chain).block();
        assertThat(seen.get().getRequest().getHeaders().get("X-Client-IP")).containsExactly("203.0.113.7");
    }
}
