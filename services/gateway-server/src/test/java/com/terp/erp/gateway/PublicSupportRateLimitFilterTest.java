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

class PublicSupportRateLimitFilterTest {
    private static final String PORTAL = "/api/v1/public/support/test-co";

    private final AtomicInteger passed = new AtomicInteger();
    private final WebFilterChain chain = ex -> {
        passed.incrementAndGet();
        return Mono.empty();
    };
    private final PublicSupportRateLimitFilter filter = new PublicSupportRateLimitFilter(
            new RateLimiter(60, 3, Clock.systemUTC()), new RateLimiter(60, 2, Clock.systemUTC()));

    @Test
    void theFourWritesShareOneTighterBucketPerIp() {
        assertThat(post(PORTAL + "/tickets", "10.0.0.1").getResponse().getStatusCode()).isNull();
        assertThat(post(PORTAL + "/tickets/track", "10.0.0.1").getResponse().getStatusCode()).isNull();
        MockServerWebExchange third = post(PORTAL + "/contact", "10.0.0.1");
        assertThat(third.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(third.getResponse().getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(post(PORTAL + "/tickets/reply", "10.0.0.2").getResponse().getStatusCode()).isNull();
        // reads are a separate bucket
        assertThat(get(PORTAL, "10.0.0.1").getResponse().getStatusCode()).isNull();
        assertThat(passed.get()).isEqualTo(4);
    }

    @Test
    void readsAreLimited() {
        for (int i = 0; i < 3; i++) {
            assertThat(get(PORTAL + "/faqs", "10.0.0.3").getResponse().getStatusCode()).isNull();
        }
        assertThat(get(PORTAL + "/kb", "10.0.0.3").getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void otherPostsOnThePrefixAndOtherPathsAreLeftToSecurity() {
        for (int i = 0; i < 10; i++) {
            post(PORTAL + "/tickets/1/close", "10.0.0.9");
            get("/api/v1/support/tickets", "10.0.0.9");
        }
        assertThat(passed.get()).isEqualTo(20);
    }

    private MockServerWebExchange get(String path, String ip) {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.get(path)
                .remoteAddress(new InetSocketAddress(ip, 1)).build());
        filter.filter(ex, chain).block();
        return ex;
    }

    private MockServerWebExchange post(String path, String ip) {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.post(path)
                .remoteAddress(new InetSocketAddress(ip, 1)).build());
        filter.filter(ex, chain).block();
        return ex;
    }
}
