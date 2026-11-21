package com.terp.erp.attendance.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import com.terp.erp.attendance.biometric.BiometricDeviceRepository;
import com.terp.erp.attendance.biometric.DeviceKeyFilter;
import com.terp.erp.security.RemoteAuthorityResolver;
import com.terp.erp.security.ResourceServerSecurity;

import java.time.Duration;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    /** The only endpoint a biometric device calls; the gateway lets it through without a token. */
    public static final String PUNCHES = "/api/v1/attendance/biometric/punches";

    private static final String[] PUBLIC = {
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    RemoteAuthorityResolver authorityResolver(AttendanceProperties props) {
        return new RemoteAuthorityResolver(props.iamServiceUrl(), Duration.ofSeconds(60));
    }

    /** Device punches: authenticated by the per-device key, never by a Keycloak token. */
    @Bean
    @Order(1)
    SecurityFilterChain deviceChain(HttpSecurity http, BiometricDeviceRepository devices, ObjectMapper mapper,
                                    DeviceRateLimitProperties limits, java.time.Clock clock) throws Exception {
        return http
                .securityMatcher(r -> "POST".equals(r.getMethod()) && PUNCHES.equals(r.getRequestURI()))
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new DeviceKeyFilter(devices, mapper, limits, clock), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().hasRole("BIOMETRIC_DEVICE"))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain filterChain(HttpSecurity http, RemoteAuthorityResolver resolver, ObjectMapper mapper)
            throws Exception {
        return ResourceServerSecurity.apply(http, resolver, mapper, PUBLIC).build();
    }
}
