package com.terp.erp.iam.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Keycloak token endpoint response (snake_case on the wire). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakTokens(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("refresh_expires_in") long refreshExpiresIn,
        @JsonProperty("token_type") String tokenType) {
}
