package com.terp.erp.delivery.config;

import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.CustomerPrincipal;
import com.terp.erp.security.ErpPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing
public class AuditingConfig {
    /** created_by / updated_by: the customer's subject (customer realm), a staff Keycloak id, or "system". */
    @Bean
    AuditorAware<String> auditorAware() {
        return () -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CustomerPrincipal c) {
                return Optional.of(c.sub());
            }
            return Optional.of(CurrentUser.find().map(ErpPrincipal::keycloakId).orElse("system"));
        };
    }
}
