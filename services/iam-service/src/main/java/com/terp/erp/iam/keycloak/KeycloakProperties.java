package com.terp.erp.iam.keycloak;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param serverUrl         base URL this service uses to reach Keycloak (inside docker: http://keycloak:8080)
 * @param realm             realm holding ERP users
 * @param clientId          public client the FE logs in with (direct access grant through this service)
 * @param adminClientId     confidential client whose service account may manage users
 * @param adminClientSecret secret of {@code adminClientId} — from the environment only
 */
@Validated
@ConfigurationProperties(prefix = "erp.keycloak")
public record KeycloakProperties(
        @NotBlank String serverUrl,
        @NotBlank String realm,
        @NotBlank String clientId,
        @NotBlank String adminClientId,
        @NotBlank String adminClientSecret) {

    public String tokenUrl() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    public String logoutUrl() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/logout";
    }

    public String adminUrl() {
        return serverUrl + "/admin/realms/" + realm;
    }
}
