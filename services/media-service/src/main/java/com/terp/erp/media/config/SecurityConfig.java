package com.terp.erp.media.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.security.RemoteAuthorityResolver;
import com.terp.erp.security.ResourceServerSecurity;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * The file content endpoint carries no token: a browser tab or an &lt;img&gt; cannot send one. It is protected by
     * an HMAC signature with an expiry instead, issued only after the permission and company checks.
     */
    private static final String[] PUBLIC = {
            "/api/v1/media/files/*/content",
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    RemoteAuthorityResolver authorityResolver(MediaProperties props) {
        return new RemoteAuthorityResolver(props.iamServiceUrl(), Duration.ofSeconds(60));
    }

    /**
     * Service-to-service endpoints. A service-account token has no IAM user, so the user chain (authorities from
     * IAM) does not apply: the token must be valid (same issuer and keys) and issued to one of the allowed clients (azp).
     */
    @Bean
    @Order(1)
    SecurityFilterChain internalChain(HttpSecurity http, MediaProperties props, ObjectMapper mapper) throws Exception {
        return http
                .securityMatcher("/api/v1/internal/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().access((auth, ctx) -> new AuthorizationDecision(
                        auth.get() instanceof JwtAuthenticationToken jwt
                                && props.internalClientIds().contains(Objects.toString(jwt.getToken().getClaimAsString("azp"), "")))))
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
    SecurityFilterChain filterChain(HttpSecurity http, RemoteAuthorityResolver resolver, ObjectMapper mapper)
            throws Exception {
        return ResourceServerSecurity.apply(http, resolver, mapper, PUBLIC).build();
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
