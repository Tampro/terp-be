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

class DeviceRateLimitFilterTest {
    private final AtomicInteger passed = new AtomicInteger();
    private final WebFilterChain chain = ex -> {
        passed.incrementAndGet();
        return Mono.empty();
    };
    private final DeviceRateLimitFilter filter = new DeviceRateLimitFilter(new RateLimiter(60, 2, Clock.systemUTC()));

    @Test
    void devicePathIsLimitedPerClientIp() {
        assertThat(run("10.0.0.1").getResponse().getStatusCode()).isNull();
        run("10.0.0.1");
        MockServerWebExchange third = run("10.0.0.1");
        assertThat(third.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(third.getResponse().getHeaders().getFirst("Retry-After")).isNotBlank();
        run("10.0.0.2");
        assertThat(passed.get()).isEqualTo(3);
    }

    @Test
    void otherPathsAndMethodsAreNotLimited() {
        for (int i = 0; i < 10; i++) {
            filter.filter(MockServerWebExchange.from(MockServerHttpRequest.get(DeviceRateLimitFilter.PATH)
                    .remoteAddress(new InetSocketAddress("10.0.0.9", 1)).build()), chain).block();
            filter.filter(MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/employees")
                    .remoteAddress(new InetSocketAddress("10.0.0.9", 1)).build()), chain).block();
        }
        assertThat(passed.get()).isEqualTo(20);
    }

    private MockServerWebExchange run(String ip) {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.post(DeviceRateLimitFilter.PATH)
                .remoteAddress(new InetSocketAddress(ip, 1234)).build());
        filter.filter(ex, chain).block();
        return ex;
    }
}
