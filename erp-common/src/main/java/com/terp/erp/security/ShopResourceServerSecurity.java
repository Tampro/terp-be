package com.terp.erp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import com.terp.erp.exception.ECommonError;

import java.util.List;

/**
 * The customer side of a service (ADR-001): one path pattern (e.g. {@code /api/v1/shop/**}) accepts only tokens of realm
 * erp-customer, decoded with that realm's keys and issuer. Use it in a chain ordered before the staff chain of
 * {@link ResourceServerSecurity}; a staff token fails here (other issuer) and a customer token fails there.
 */
public final class ShopResourceServerSecurity {
    private ShopResourceServerSecurity() {
    }

    /** Decoder that rejects any token whose issuer is not {@code issuer}, keys fetched from {@code jwkSetUri}. */
    public static JwtDecoder customerDecoder(String jwkSetUri, String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    public static HttpSecurity apply(HttpSecurity http, JwtDecoder customerDecoder, ObjectMapper mapper,
                                     String pathPattern) throws Exception {
        return http
                .securityMatcher(pathPattern)
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(customerDecoder).jwtAuthenticationConverter(ShopResourceServerSecurity::toAuthentication))
                        .authenticationEntryPoint((req, res, ex) ->
                                ResourceServerSecurity.write(res, mapper, HttpStatus.UNAUTHORIZED, ECommonError.COMMON_003))
                        .accessDeniedHandler((req, res, ex) ->
                                ResourceServerSecurity.write(res, mapper, HttpStatus.FORBIDDEN, ECommonError.COMMON_004)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                ResourceServerSecurity.write(res, mapper, HttpStatus.UNAUTHORIZED, ECommonError.COMMON_003))
                        .accessDeniedHandler((req, res, ex) ->
                                ResourceServerSecurity.write(res, mapper, HttpStatus.FORBIDDEN, ECommonError.COMMON_004)));
    }

    /** No authorities: a customer is never checked by PermissionChecker; the principal is the customer. */
    static AbstractAuthenticationToken toAuthentication(Jwt jwt) {
        CustomerPrincipal principal = new CustomerPrincipal(jwt.getSubject(), jwt.getClaimAsString("azp"),
                jwt.getClaimAsString("email"));
        AbstractAuthenticationToken token = new AbstractAuthenticationToken(List.of()) {
            @Override
            public Object getCredentials() {
                return jwt;
            }

            @Override
            public Object getPrincipal() {
                return principal;
            }
        };
        token.setAuthenticated(true);
        return token;
    }
}
