package com.terp.erp.gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

/**
 * Per-client-IP token buckets on the public support portal (no token): one for reads, a much tighter one shared by
 * the four writes — create a ticket, track a ticket (number + email must not be guessable by brute force), reply to a
 * ticket, contact form. Runs before security, so a flood is refused before any work: 429 COMMON_006 with Retry-After.
 * Other methods on the prefix are left to security (denied).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicSupportRateLimitFilter implements WebFilter {
    static final String PREFIX = "/api/v1/public/support/";
    static final String[] WRITES = {"/api/v1/public/support/*/tickets", "/api/v1/public/support/*/tickets/track",
            "/api/v1/public/support/*/tickets/reply", "/api/v1/public/support/*/contact"};
    private static final List<PathPattern> WRITE_PATTERNS = Arrays.stream(WRITES)
            .map(PathPatternParser.defaultInstance::parse).toList();

    private final RateLimiter reads;
    private final RateLimiter writes;

    @Autowired
    public PublicSupportRateLimitFilter(
            @Value("${erp.gateway.support-rate-limit.read-per-minute:120}") int readPerMinute,
            @Value("${erp.gateway.support-rate-limit.read-burst:40}") int readBurst,
            @Value("${erp.gateway.support-rate-limit.write-per-minute:10}") int writePerMinute,
            @Value("${erp.gateway.support-rate-limit.write-burst:5}") int writeBurst) {
        this(new RateLimiter(readPerMinute, readBurst, Clock.systemUTC()),
                new RateLimiter(writePerMinute, writeBurst, Clock.systemUTC()));
    }

    PublicSupportRateLimitFilter(RateLimiter reads, RateLimiter writes) {
        this.reads = reads;
        this.writes = writes;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        var req = exchange.getRequest();
        String path = req.getPath().value();
        if (!path.startsWith(PREFIX)) {
            return chain.filter(exchange);
        }
        RateLimiter limiter;
        if (req.getMethod() == HttpMethod.GET) {
            limiter = reads;
        } else if (req.getMethod() == HttpMethod.POST && isWrite(path)) {
            limiter = writes;
        } else {
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

    private static boolean isWrite(String path) {
        PathContainer p = PathContainer.parsePath(path);
        return WRITE_PATTERNS.stream().anyMatch(w -> w.matches(p));
    }
}
