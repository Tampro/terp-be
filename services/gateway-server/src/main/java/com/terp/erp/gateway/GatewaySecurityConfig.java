package com.terp.erp.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * First line only: the gateway checks that a token is present, signed by Keycloak and not expired. Permissions and
 * company isolation are decided by each service (which validates the token again), never here — a request that
 * reaches a service directly is still checked.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private static final String[] SWAGGER_PATHS = {"/swagger-ui.html", "/swagger-ui/**", "/webjars/**",
            "/v3/api-docs/**", "/docs/*/v3/api-docs"};

    /**
     * Customer side (ADR-001): /api/v1/shop/** accepts only tokens of realm erp-customer (own keys, own issuer), so a
     * staff token is refused here and a customer token is refused by the staff chain below.
     */
    @Bean
    @Order(1)
    SecurityWebFilterChain shopFilterChain(ServerHttpSecurity http,
                                           @Value("${erp.gateway.customer.jwk-set-uri}") String jwkSetUri,
                                           @Value("${erp.gateway.customer.issuer}") String issuer) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(SHOP_PATH))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(ex -> ex.pathMatchers(HttpMethod.OPTIONS).permitAll().anyExchange().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtDecoder(customerDecoder(jwkSetUri, issuer)))
                        .authenticationEntryPoint((exchange, ex) -> write(exchange, HttpStatus.UNAUTHORIZED,
                                "COMMON_003", "Authentication required or the token is invalid.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) -> write(exchange, HttpStatus.UNAUTHORIZED,
                                "COMMON_003", "Authentication required or the token is invalid."))
                        .accessDeniedHandler((exchange, ex) -> write(exchange, HttpStatus.FORBIDDEN,
                                "COMMON_004", "You do not have permission to perform this action.")))
                .build();
    }

    static final String SHOP_PATH = "/api/v1/shop/**";

    static ReactiveJwtDecoder customerDecoder(String jwkSetUri, String issuer) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    @Order(2)
    SecurityWebFilterChain gatewayFilterChain(ServerHttpSecurity http,
                                               @Value("${erp.swagger.enabled:false}") boolean swaggerEnabled) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh-token",
                                "/api/v1/auth/logout", "/api/v1/auth/initial-password",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password", "/api/v1/auth/register",
                                "/api/v1/auth/verify-email", "/api/v1/auth/resend-verification").permitAll()
                        // Signed, time-limited file links: the signature is checked by media-service.
                        .pathMatchers(HttpMethod.GET, "/api/v1/media/files/*/content").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/settings/public").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/languages", "/api/v1/languages/*/labels").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/public/site/landing", "/api/v1/public/site/pages",
                                "/api/v1/public/site/pages/*").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/public/site/contact", "/api/v1/public/site/newsletter",
                                "/api/v1/public/site/newsletter/unsubscribe", "/api/v1/public/site/cookie-consent",
                                "/api/v1/public/payments/stripe/webhook")
                                .permitAll()
                        // Biometric devices cannot log in: attendance-service checks their per-device key instead.
                        .pathMatchers(HttpMethod.POST, "/api/v1/attendance/biometric/punches").permitAll()
                        // Public career page: read and apply only, limited per IP (CareerRateLimitFilter).
                        .pathMatchers(HttpMethod.GET, "/api/v1/public/career/**").permitAll()
                        .pathMatchers(HttpMethod.POST, CareerRateLimitFilter.APPLY).permitAll()
                        // Public form link: read the form and submit a response only, limited per IP
                        // (PublicFormRateLimitFilter); project-service checks the form token and that it is active.
                        .pathMatchers(HttpMethod.GET, "/api/v1/public/forms/*").permitAll()
                        .pathMatchers(HttpMethod.POST, PublicFormRateLimitFilter.APPLY).permitAll()
                        // Public support portal: reads, and exactly four writes (create / track / reply to a ticket,
                        // contact form), limited per IP (PublicSupportRateLimitFilter); support-service checks the
                        // slug, that the portal is enabled, and ticket number + email or the tracking token.
                        .pathMatchers(HttpMethod.GET, "/api/v1/public/support/**").permitAll()
                        .pathMatchers(HttpMethod.POST, PublicSupportRateLimitFilter.WRITES).permitAll()
                        // Public storefront: reads only, limited per IP (PublicShopRateLimitFilter); store-service checks
                        // the slug, that the shop is enabled and the company active, and scopes every read to it.
                        .pathMatchers(HttpMethod.GET, "/api/v1/public/shop/**").permitAll()
                        .pathMatchers("/actuator/health/**").permitAll()
                        // API docs only (no data); the endpoints they describe still need a token.
                        .pathMatchers(HttpMethod.GET, swaggerEnabled ? SWAGGER_PATHS : new String[0]).permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((exchange, ex) -> write(exchange, HttpStatus.UNAUTHORIZED,
                                "COMMON_003", "Authentication required or the token is invalid.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) -> write(exchange, HttpStatus.UNAUTHORIZED,
                                "COMMON_003", "Authentication required or the token is invalid."))
                        .accessDeniedHandler((exchange, ex) -> write(exchange, HttpStatus.FORBIDDEN,
                                "COMMON_004", "You do not have permission to perform this action.")))
                .build();
    }

    // Same envelope as the services, so the FE handles a gateway 401 like any other.
    private static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        var res = exchange.getResponse();
        res.setStatusCode(status);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"result\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\"}";
        return res.writeWith(Mono.just(res.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
