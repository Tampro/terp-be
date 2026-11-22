package com.terp.erp.document.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Keycloak service account of document-service (client credentials), used only to get the signed link of an HR
 * document file for a caller who may download the document but not open the media library (no MEDIA_VIEW), after
 * document-service has checked the download rules itself.
 *
 * @param tokenUrl     Keycloak token endpoint (inside the network)
 * @param clientId     confidential client with service accounts enabled (realm erp)
 * @param clientSecret environment only, no default
 */
@Validated
@ConfigurationProperties(prefix = "erp.document.service-account")
public record ServiceAccountProperties(
        @NotBlank String tokenUrl,
        @NotBlank String clientId,
        @NotBlank String clientSecret) {
}
