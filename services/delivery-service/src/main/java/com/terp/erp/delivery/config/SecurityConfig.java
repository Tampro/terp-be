package com.terp.erp.delivery.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.security.RemoteAuthorityResolver;
import com.terp.erp.security.ResourceServerSecurity;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Two chains, both staff realm: /api/v1/internal/** for allowed service accounts only (order-service cancels the
 * delivery of a paid order), every other path for Back Office and riders with authorities from IAM per request.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private static final String[] PUBLIC = {"/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    @Bean
    RemoteAuthorityResolver authorityResolver(DeliveryProperties props) {
        return new RemoteAuthorityResolver(props.iamServiceUrl(), Duration.ofSeconds(60));
    }

    @Bean
    @Order(1)
    SecurityFilterChain internalChain(HttpSecurity http, InternalClientProperties clients, ObjectMapper mapper) throws Exception {
        return http
                .securityMatcher("/api/v1/internal/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().access((auth, ctx) -> new AuthorizationDecision(
                        internalCaller(auth.get(), clients.clientIds()))))
                .oauth2ResourceServer(o -> o.jwt(j -> { })
                        .authenticationEntryPoint((req, res, ex) -> write(res, mapper, HttpStatus.UNAUTHORIZED, ECommonError.COMMON_003)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(res, mapper, HttpStatus.UNAUTHORIZED, ECommonError.COMMON_003))
                        .accessDeniedHandler((req, res, ex) -> write(res, mapper, HttpStatus.FORBIDDEN, ECommonError.COMMON_004)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain filterChain(HttpSecurity http, RemoteAuthorityResolver resolver, ObjectMapper mapper)
            throws Exception {
        return ResourceServerSecurity.apply(http, resolver, mapper, PUBLIC).build();
    }

    /** A valid staff-realm JWT issued to one of the allowed service-account clients (azp); a user token never matches. */
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
}
