package com.terp.erp.iam.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * First company and its administrator, created at startup when username and password are both set
 * (env ADMIN_USER / ADMIN_PASSWORD). Unset → nothing happens.
 */
@ConfigurationProperties(prefix = "erp.bootstrap")
public record BootstrapProperties(
        String adminUsername,
        String adminPassword,
        String adminEmail,
        String adminFullName,
        String companyCode,
        String companyName) {

    public boolean enabled() {
        return adminUsername != null && !adminUsername.isBlank() && adminPassword != null && !adminPassword.isBlank();
    }
}
