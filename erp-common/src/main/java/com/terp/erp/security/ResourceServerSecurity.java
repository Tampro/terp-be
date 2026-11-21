package com.terp.erp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.TraceIdFilter;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.exception.ErrorCode;

import java.io.IOException;

/**
 * The one way a servlet service configures itself as a Keycloak resource server: stateless, every path
 * authenticated except the listed public ones, 401/403 in the API envelope. Services still validate the token
 * themselves — the gateway is not trusted as the only check.
 */
public final class ResourceServerSecurity {
    private ResourceServerSecurity() {
    }

    public static HttpSecurity apply(HttpSecurity http, AuthorityResolver resolver, ObjectMapper mapper,
                                     String... publicPaths) throws Exception {
        return http
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(new ErpJwtAuthenticationConverter(resolver)))
                        .authenticationEntryPoint((req, res, ex) ->
                                write(res, mapper, HttpStatus.UNAUTHORIZED, ECommonError.COMMON_003))
                        .accessDeniedHandler((req, res, ex) ->
                                write(res, mapper, HttpStatus.FORBIDDEN, ECommonError.COMMON_004)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                write(res, mapper, HttpStatus.UNAUTHORIZED, ECommonError.COMMON_003))
                        .accessDeniedHandler((req, res, ex) ->
                                write(res, mapper, HttpStatus.FORBIDDEN, ECommonError.COMMON_004)));
    }

    // Filter-level errors are written before any MessageSource lookup, so they carry the English text; the FE keys
    // on `code`, and 401 is the only status that triggers its token refresh.
    private static void write(HttpServletResponse res, ObjectMapper mapper, HttpStatus status, ErrorCode error)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), ApiMessageDto.builder()
                .result(false).code(error.getCode()).message(error.getMessage())
                .traceId(MDC.get(TraceIdFilter.MDC_KEY)).build());
    }
}
