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

class PublicFormRateLimitFilterTest {
    private static final String JOBS = "/api/v1/public/forms/test-token";
    private static final String APPLY = "/api/v1/public/forms/test-token/responses";

    private final AtomicInteger passed = new AtomicInteger();
    private final WebFilterChain chain = ex -> {
        passed.incrementAndGet();
        return Mono.empty();
    };
    private final PublicFormRateLimitFilter filter = new PublicFormRateLimitFilter(new RateLimiter(60, 3, Clock.systemUTC()),
            new RateLimiter(60, 1, Clock.systemUTC()));

    @Test
    void applicationsHaveTheirOwnTighterBucketPerIp() {
        assertThat(post(APPLY, "10.0.0.1").getResponse().getStatusCode()).isNull();
        MockServerWebExchange second = post(APPLY, "10.0.0.1");
        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.getResponse().getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(post(APPLY, "10.0.0.2").getResponse().getStatusCode()).isNull();
        // reads are a separate bucket
        assertThat(get(JOBS, "10.0.0.1").getResponse().getStatusCode()).isNull();
        assertThat(passed.get()).isEqualTo(3);
    }

    @Test
    void readsAreLimited() {
        for (int i = 0; i < 3; i++) {
            assertThat(get(JOBS, "10.0.0.3").getResponse().getStatusCode()).isNull();
        }
        assertThat(get(JOBS + "/1", "10.0.0.3").getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void otherPathsAreNotLimited() {
        for (int i = 0; i < 10; i++) {
            get("/api/v1/recruitment/candidates", "10.0.0.9");
            post("/api/v1/public/career/test-co/jobs", "10.0.0.9");
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
