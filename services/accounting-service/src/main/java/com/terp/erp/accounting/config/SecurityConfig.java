package com.terp.erp.accounting.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import com.terp.erp.security.RemoteAuthorityResolver;
import com.terp.erp.security.ResourceServerSecurity;

import java.time.Duration;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private static final String[] PUBLIC = {
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    RemoteAuthorityResolver authorityResolver(AccountingProperties props) {
        return new RemoteAuthorityResolver(props.iamServiceUrl(), Duration.ofSeconds(60));
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, RemoteAuthorityResolver resolver, ObjectMapper mapper)
            throws Exception {
        return ResourceServerSecurity.apply(http, resolver, mapper, PUBLIC).build();
    }
}
