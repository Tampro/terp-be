package com.terp.erp.document.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;

import java.util.Optional;

@Configuration
@EnableJpaAuditing
public class AuditingConfig {
    /** created_by / updated_by hold the Keycloak subject; "system" for writes made outside a request. */
    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of(CurrentUser.find().map(ErpPrincipal::keycloakId).orElse("system"));
    }
}
