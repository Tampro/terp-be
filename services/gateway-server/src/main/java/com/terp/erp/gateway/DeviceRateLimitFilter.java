package com.terp.erp.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Per-client-IP token bucket on the biometric device endpoint, the only one the gateway lets through without a token.
 * Runs before security, so a flood is refused before any work: 429 COMMON_006 with Retry-After. attendance-service
 * limits again per device key and per wrong key.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DeviceRateLimitFilter implements WebFilter {
    static final String PATH = "/api/v1/attendance/biometric/punches";

    private final RateLimiter limiter;

    @org.springframework.beans.factory.annotation.Autowired
    public DeviceRateLimitFilter(@Value("${erp.gateway.device-rate-limit.per-minute:60}") int perMinute,
                                 @Value("${erp.gateway.device-rate-limit.burst:20}") int burst) {
        this(new RateLimiter(perMinute, burst, Clock.systemUTC()));
    }

    DeviceRateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        var req = exchange.getRequest();
        if (req.getMethod() != HttpMethod.POST || !PATH.equals(req.getPath().value())) {
            return chain.filter(exchange);
        }
        InetSocketAddress remote = req.getRemoteAddress();
        String ip = remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
        long wait = limiter.tryAcquire(ip);
        if (wait == 0) {
            return chain.filter(exchange);
        }
        var res = exchange.getResponse();
        res.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        res.getHeaders().set("Retry-After", String.valueOf(wait));
        byte[] body = "{\"result\":false,\"code\":\"COMMON_006\",\"message\":\"Too many requests. Please try again later.\"}"
                .getBytes(StandardCharsets.UTF_8);
        return res.writeWith(Mono.just(res.bufferFactory().wrap(body)));
    }
}
