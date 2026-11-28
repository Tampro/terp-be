package com.terp.erp.member.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.security.RemoteAuthorityResolver;
import com.terp.erp.security.ResourceServerSecurity;
import com.terp.erp.security.ShopResourceServerSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;

/**
 * Two realms (ADR-001): /api/v1/shop/** takes customer tokens only (realm erp-customer); every other path keeps the
 * staff rules (realm erp, authorities from IAM). Neither token passes the other chain: different issuer and keys.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    public static final String SHOP_PATH = "/api/v1/shop/**";
    private static final String[] PUBLIC = {"/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    @Bean
    RemoteAuthorityResolver authorityResolver(MemberProperties props) {
        return new RemoteAuthorityResolver(props.iamServiceUrl(), Duration.ofSeconds(60));
    }

    @Bean
    @Order(1)
    SecurityFilterChain shopChain(HttpSecurity http, MemberProperties props, ObjectMapper mapper) throws Exception {
        return ShopResourceServerSecurity.apply(http,
                ShopResourceServerSecurity.customerDecoder(props.customerJwkSetUri(), props.customerIssuer()), mapper,
                SHOP_PATH).build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain filterChain(HttpSecurity http, RemoteAuthorityResolver resolver, ObjectMapper mapper)
            throws Exception {
        return ResourceServerSecurity.apply(http, resolver, mapper, PUBLIC).build();
    }
}
