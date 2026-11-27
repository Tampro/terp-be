package com.terp.erp.store.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.security.RemoteAuthorityResolver;
import com.terp.erp.security.ResourceServerSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    // No public business endpoint yet: the storefront (/api/v1/public/shop/**) is a later, separately approved change.
    private static final String[] PUBLIC = {
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    RemoteAuthorityResolver authorityResolver(StoreProperties props) {
        return new RemoteAuthorityResolver(props.iamServiceUrl(), Duration.ofSeconds(60));
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, RemoteAuthorityResolver resolver, ObjectMapper mapper)
            throws Exception {
        return ResourceServerSecurity.apply(http, resolver, mapper, PUBLIC).build();
    }
}
