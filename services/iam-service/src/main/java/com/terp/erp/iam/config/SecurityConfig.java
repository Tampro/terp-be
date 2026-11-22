package com.terp.erp.iam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.security.ResourceServerSecurity;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Login, refresh and logout are reached without an access token; everything else needs one. */
    private static final String[] PUBLIC = {
            "/api/v1/auth/login",
            "/api/v1/auth/refresh-token",
            "/api/v1/auth/logout",
            "/api/v1/auth/initial-password",
            // Account self-service (docs/analysis/06 §2.4-2.6): rate limited, never reveal whether an email exists.
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/register",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/settings/public",
            // Language selector and UI labels: the login page needs them (docs/analysis/06 §3).
            "/api/v1/languages",
            "/api/v1/languages/*/labels",
            // Public website (docs/analysis/06 §3.2.7-3.2.10): landing, pages, contact form, newsletter.
            "/api/v1/public/site/**",
            // Stripe webhook: authenticated by the Stripe-Signature header, not by a token.
            "/api/v1/public/payments/stripe/webhook",
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    /**
     * Service-to-service endpoints (copy of media-service's internalChain). A service-account token has no IAM user,
     * so the user chain (authorities from IAM) does not apply: the token must be valid (same issuer and keys) and
     * issued to one of the allowed clients (azp). Not routed by the gateway.
     */
    @Bean
    @Order(1)
    SecurityFilterChain internalChain(HttpSecurity http, InternalClientProperties props, ObjectMapper mapper)
            throws Exception {
        return http
                .securityMatcher("/api/v1/internal/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().access((auth, ctx) -> new AuthorizationDecision(
                        internalCaller(auth.get(), props.internalClientIds()))))
                .oauth2ResourceServer(o -> o.jwt(j -> { })
                        .authenticationEntryPoint((req, res, ex) -> write(res, mapper, HttpStatus.UNAUTHORIZED,
                                ECommonError.COMMON_003)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(res, mapper, HttpStatus.UNAUTHORIZED,
                                ECommonError.COMMON_003))
                        .accessDeniedHandler((req, res, ex) -> write(res, mapper, HttpStatus.FORBIDDEN,
                                ECommonError.COMMON_004)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain filterChain(HttpSecurity http, LocalAuthorityResolver resolver, ObjectMapper mapper)
            throws Exception {
        return ResourceServerSecurity.apply(http, resolver, mapper, PUBLIC).build();
    }

    /** A valid JWT issued to one of the allowed service-account clients (azp); a user token never matches. */
    static boolean internalCaller(Authentication auth, List<String> allowedClients) {
        return auth instanceof JwtAuthenticationToken jwt
                && allowedClients.contains(Objects.toString(jwt.getToken().getClaimAsString("azp"), ""));
    }

    private static void write(HttpServletResponse res, ObjectMapper mapper, HttpStatus status, ErrorCode error)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), ApiMessageDto.builder()
                .result(false).code(error.getCode()).message(error.getMessage()).build());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${erp.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language", "X-Trace-Id"));
        cors.setExposedHeaders(List.of("X-Trace-Id"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
