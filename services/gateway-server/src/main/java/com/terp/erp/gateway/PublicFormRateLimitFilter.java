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

/**
 * Per-client-IP token buckets on the public form link (no token): one for reads, a much tighter one for
 * submitted responses (each stores a row). Runs before security, so a flood is refused before any work: 429 COMMON_006 with
 * Retry-After. Other methods on the prefix are left to security (denied).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicFormRateLimitFilter implements WebFilter {
    static final String PREFIX = "/api/v1/public/forms/";
    static final String APPLY = "/api/v1/public/forms/*/responses";
    private static final PathPattern APPLY_PATTERN = PathPatternParser.defaultInstance.parse(APPLY);

    private final RateLimiter reads;
    private final RateLimiter applies;

    @Autowired
    public PublicFormRateLimitFilter(@Value("${erp.gateway.form-rate-limit.read-per-minute:120}") int readPerMinute,
                                 @Value("${erp.gateway.form-rate-limit.read-burst:40}") int readBurst,
                                 @Value("${erp.gateway.form-rate-limit.apply-per-minute:10}") int applyPerMinute,
                                 @Value("${erp.gateway.form-rate-limit.apply-burst:5}") int applyBurst) {
        this(new RateLimiter(readPerMinute, readBurst, Clock.systemUTC()),
                new RateLimiter(applyPerMinute, applyBurst, Clock.systemUTC()));
    }

    PublicFormRateLimitFilter(RateLimiter reads, RateLimiter applies) {
        this.reads = reads;
        this.applies = applies;
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
        } else if (req.getMethod() == HttpMethod.POST && APPLY_PATTERN.matches(PathContainer.parsePath(path))) {
            limiter = applies;
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
}
