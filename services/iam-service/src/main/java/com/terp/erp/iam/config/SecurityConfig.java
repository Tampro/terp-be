package com.terp.erp.iam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import com.terp.erp.security.ResourceServerSecurity;

import java.util.List;

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

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, LocalAuthorityResolver resolver, ObjectMapper mapper)
            throws Exception {
        return ResourceServerSecurity.apply(http, resolver, mapper, PUBLIC).build();
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
