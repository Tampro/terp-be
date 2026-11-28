package com.terp.erp.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PublicShopRateLimitFilterTest {
    private static final String SHOP = "/api/v1/public/shop/test-co";

    private final AtomicInteger passed = new AtomicInteger();
    private final WebFilterChain chain = ex -> {
        passed.incrementAndGet();
        return Mono.empty();
    };
    private final PublicShopRateLimitFilter filter = new PublicShopRateLimitFilter(new RateLimiter(60, 3, Clock.systemUTC()));

    @Test
    void readsAreLimitedPerIp() {
        for (int i = 0; i < 3; i++) {
            assertThat(send(MockServerHttpRequest.get(SHOP + "/categories"), "10.0.0.1").getResponse().getStatusCode()).isNull();
        }
        MockServerWebExchange fourth = send(MockServerHttpRequest.get(SHOP + "/stores/1/items"), "10.0.0.1");
        assertThat(fourth.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(fourth.getResponse().getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(send(MockServerHttpRequest.get(SHOP + "/categories"), "10.0.0.2").getResponse().getStatusCode()).isNull();
        assertThat(passed.get()).isEqualTo(4);
    }

    @Test
    void otherMethodsAndPathsAreLeftToSecurity() {
        for (int i = 0; i < 10; i++) {
            send(MockServerHttpRequest.post(SHOP + "/categories"), "10.0.0.9");
            send(MockServerHttpRequest.get("/api/v1/store/stores"), "10.0.0.9");
        }
        assertThat(passed.get()).isEqualTo(20);
    }

    private MockServerWebExchange send(MockServerHttpRequest.BaseBuilder<?> req, String ip) {
        MockServerWebExchange ex = MockServerWebExchange.from(req.remoteAddress(new InetSocketAddress(ip, 1)).build());
        filter.filter(ex, chain).block();
        return ex;
    }
}
