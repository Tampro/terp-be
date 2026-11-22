package com.terp.erp.iam.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The /api/v1/internal/** rule: only a JWT issued to an allowed service-account client (azp) passes (else 403). */
class InternalChainTest {
    private static final List<String> ALLOWED = List.of("erp-notification-service");

    private static JwtAuthenticationToken token(String azp) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject("sub-1")
                .issuedAt(Instant.parse("2026-11-22T00:00:00Z")).expiresAt(Instant.parse("2026-11-22T01:00:00Z"));
        if (azp != null) {
            b.claim("azp", azp);
        }
        return new JwtAuthenticationToken(b.build());
    }

    @Test
    void acceptsTheConfiguredServiceAccount() {
        assertThat(SecurityConfig.internalCaller(token("erp-notification-service"), ALLOWED)).isTrue();
    }

    @Test
    void refusesUserTokensAndOtherClients() {
        assertThat(SecurityConfig.internalCaller(token("erp-web"), ALLOWED)).isFalse();
        assertThat(SecurityConfig.internalCaller(token("erp-document-service"), ALLOWED)).isFalse();
        assertThat(SecurityConfig.internalCaller(token(null), ALLOWED)).isFalse();
        assertThat(SecurityConfig.internalCaller(new UsernamePasswordAuthenticationToken("u", "p", List.of()),
                ALLOWED)).isFalse();
        assertThat(SecurityConfig.internalCaller(null, ALLOWED)).isFalse();
    }

    @Test
    void theInternalChainRunsBeforeTheUserChain() throws NoSuchMethodException {
        int internal = SecurityConfig.class.getDeclaredMethod("internalChain",
                org.springframework.security.config.annotation.web.builders.HttpSecurity.class,
                InternalClientProperties.class, com.fasterxml.jackson.databind.ObjectMapper.class)
                .getAnnotation(Order.class).value();
        int user = SecurityConfig.class.getDeclaredMethod("filterChain",
                org.springframework.security.config.annotation.web.builders.HttpSecurity.class,
                com.terp.erp.iam.security.LocalAuthorityResolver.class,
                com.fasterxml.jackson.databind.ObjectMapper.class).getAnnotation(Order.class).value();
        assertThat(internal).isLessThan(user);
    }
}
