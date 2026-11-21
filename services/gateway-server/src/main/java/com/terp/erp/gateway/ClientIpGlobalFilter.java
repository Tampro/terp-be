package com.terp.erp.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Tells the services who is calling: X-Client-IP = the address of the connection to the gateway. A client-sent
 * X-Client-IP is replaced, so it cannot fake the address (X-Forwarded-For is passed on as sent and is not trusted;
 * PO 2026-11-21). Used by the IAM login history and IP restriction.
 * TODO(assumption): behind a load balancer this is the balancer's address — configure it when deploying.
 */
@Component
public class ClientIpGlobalFilter implements GlobalFilter, Ordered {
    static final String HEADER = "X-Client-IP";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String ip = remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress();
        ServerWebExchange mutated = exchange.mutate().request(r -> r.headers(h -> {
            h.remove(HEADER);
            if (ip != null) {
                h.set(HEADER, ip);
            }
        })).build();
        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
