package com.terp.erp.gateway;

import org.springframework.beans.factory.annotation.Autowired;
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
 * Per-client-IP token bucket on the public storefront (no token, reads only): a crawler or a flood is refused before
 * any work, 429 COMMON_006 with Retry-After. Other methods on the prefix are left to security (denied).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicShopRateLimitFilter implements WebFilter {
    static final String PREFIX = "/api/v1/public/shop/";

    private final RateLimiter reads;

    @Autowired
    public PublicShopRateLimitFilter(@Value("${erp.gateway.shop-rate-limit.read-per-minute:240}") int readPerMinute,
                                     @Value("${erp.gateway.shop-rate-limit.read-burst:60}") int readBurst) {
        this(new RateLimiter(readPerMinute, readBurst, Clock.systemUTC()));
    }

    PublicShopRateLimitFilter(RateLimiter reads) {
        this.reads = reads;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        var req = exchange.getRequest();
        if (!req.getPath().value().startsWith(PREFIX) || req.getMethod() != HttpMethod.GET) {
            return chain.filter(exchange);
        }
        InetSocketAddress remote = req.getRemoteAddress();
        String ip = remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
        long wait = reads.tryAcquire(ip);
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
