package com.terp.erp.iam.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Talks to Keycloak over its REST API: the token endpoint for the FE login facade, and the admin API (service
 * account of {@code adminClientId}) for provisioning users. Passwords pass through and are never logged or stored.
 * No retries: a login is not something to replay, and the admin calls are not idempotent (create user).
 */
@Slf4j
@Component
public class KeycloakClient {
    /** Keycloak answers this only after the password was verified, when a required action is still pending. */
    static final String NOT_FULLY_SET_UP = "Account is not fully set up";

    private final KeycloakProperties props;
    private final RestClient http;
    private final ObjectMapper mapper;
    private volatile String adminToken;
    private volatile Instant adminTokenExpiry = Instant.EPOCH;

    public KeycloakClient(KeycloakProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(3));
        rf.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(rf).build();
    }

    // ---------------------------------------------------------------- token endpoint (public client)

    public KeycloakTokens passwordGrant(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", props.clientId());
        form.add("username", username);
        form.add("password", password);
        form.add("scope", "openid");
        return token(form, true);
    }

    public KeycloakTokens refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", props.clientId());
        form.add("refresh_token", refreshToken);
        return token(form, false);
    }

    /** Ends the Keycloak session of this refresh token. Best effort: logout must not fail for the user. */
    /**
     * Returns true only when Keycloak accepted the refresh token and ended its session — the proof that the
     * caller owned that session, so its {@code sid} may be revoked.
     */
    public boolean logout(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.clientId());
        form.add("refresh_token", refreshToken);
        try {
            http.post().uri(props.logoutUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("Keycloak logout failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Reads {@code sid} from a refresh token WITHOUT verifying it. Only call after {@link #logout} returned true:
     * Keycloak has then validated the same token.
     */
    public String sessionIdOf(String refreshToken) {
        try {
            String[] parts = refreshToken.split("\\.");
            byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
            return mapper.readTree(payload).path("sid").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private KeycloakTokens token(MultiValueMap<String, String> form, boolean passwordGrant) {
        try {
            return http.post().uri(props.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(KeycloakTokens.class);
        } catch (HttpClientErrorException e) {
            String description = errorDescription(e);
            if (!passwordGrant) {
                throw ApiException.unauthorized(EIamError.AUTH_003);
            }
            if (description.contains(NOT_FULLY_SET_UP)) {
                throw ApiException.badRequest(EIamError.AUTH_004);
            }
            if (description.contains("disabled")) {
                // "Account disabled" (locked by an admin) or "Account is temporarily disabled" (brute force).
                throw ApiException.unauthorized(description.contains("temporarily")
                        ? EIamError.AUTH_008 : EIamError.AUTH_002);
            }
            throw ApiException.unauthorized(EIamError.AUTH_001);
        } catch (RestClientException e) {
            log.error("Keycloak token endpoint unreachable: {}", e.getClass().getSimpleName());
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, EIamError.AUTH_005);
        }
    }

    private String errorDescription(HttpClientErrorException e) {
        try {
            JsonNode node = mapper.readTree(e.getResponseBodyAsString());
            return node.path("error_description").asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    // ---------------------------------------------------------------- admin API (service account)

    /** Creates an enabled user with a temporary password; returns the Keycloak id. */
    public String createUser(String username, String email, String fullName, String temporaryPassword) {
        return createUser(username, email, fullName, temporaryPassword, true);
    }

    /** {@code temporary = false} only for the bootstrap admin, whose password the operator chose. */
    public String createUser(String username, String email, String fullName, String password, boolean temporary) {
        String temporaryPassword = password;
        // Keycloak's user profile requires firstName AND lastName; without lastName every login stops at
        // "Account is not fully set up" (VERIFY_PROFILE). Keycloak only displays them — ERP keeps fullName.
        String[] names = splitName(fullName);
        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "firstName", names[0],
                "lastName", names[1],
                "enabled", true,
                "emailVerified", false,
                "credentials", List.of(Map.of("type", "password", "value", temporaryPassword, "temporary", temporary)));
        try {
            ResponseEntity<Void> res = admin().post().uri(props.adminUrl() + "/users")
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            URI location = res.getHeaders().getLocation();
            if (location == null) {
                throw ApiException.badRequest(EIamError.IAM_014);
            }
            String path = location.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException.Conflict e) {
            throw ApiException.badRequest(EIamError.IAM_010);
        } catch (HttpClientErrorException.BadRequest e) {
            // Password policy or user profile validation.
            log.error("Keycloak rejected the user: {}", errorDescription(e));
            throw ApiException.badRequest(EIamError.IAM_014);
        } catch (RestClientException e) {
            log.error("Keycloak create user failed: {}", e.getClass().getSimpleName());
            throw ApiException.badRequest(EIamError.IAM_014);
        }
    }

    /** "Nguyen Van An" → first "Van An", last "Nguyen"; a one-word name is used for both. */
    static String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[]{trimmed, trimmed};
        }
        return new String[]{trimmed.substring(space + 1).trim(), trimmed.substring(0, space)};
    }

    /** Sets a permanent password and clears pending required actions (first-login password change). */
    public void setPermanentPassword(String keycloakId, String password) {
        try {
            admin().put().uri(props.adminUrl() + "/users/" + keycloakId + "/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("type", "password", "value", password, "temporary", false))
                    .retrieve().toBodilessEntity();
            admin().put().uri(props.adminUrl() + "/users/" + keycloakId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("requiredActions", List.of()))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.BadRequest e) {
            // Keycloak rejects a password that breaks the realm password policy with 400.
            throw ApiException.badRequest(EIamError.AUTH_007);
        } catch (RestClientException e) {
            log.error("Keycloak reset password failed: {}", e.getClass().getSimpleName());
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, EIamError.AUTH_005);
        }
    }

    public String findUserId(String username) {
        JsonNode users = admin().get()
                .uri(props.adminUrl() + "/users?exact=true&username={u}", username)
                .retrieve().body(JsonNode.class);
        return users != null && users.size() == 1 ? users.get(0).path("id").asText(null) : null;
    }

    /** Edit user (PO, 2026-11-21): email and the display names; the username never changes. */
    public void updateProfile(String keycloakId, String email, String fullName) {
        String[] names = splitName(fullName);
        try {
            admin().put().uri(props.adminUrl() + "/users/" + keycloakId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "firstName", names[0], "lastName", names[1]))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            throw ApiException.badRequest(EIamError.IAM_017);
        } catch (RestClientException e) {
            log.error("Keycloak update user failed: {}", e.getClass().getSimpleName());
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, EIamError.AUTH_005);
        }
    }

    /**
     * Delete user (PO, 2026-11-21): removed from Keycloak, so its sessions end and the username is free again;
     * a user Keycloak no longer has counts as deleted.
     */
    public void deleteUser(String keycloakId) {
        try {
            admin().delete().uri(props.adminUrl() + "/users/" + keycloakId).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            log.info("Keycloak user already gone");
        } catch (RestClientException e) {
            log.error("Keycloak delete user failed: {}", e.getClass().getSimpleName());
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, EIamError.AUTH_005);
        }
    }

    /** Ends every Keycloak session of the user (their refresh tokens stop working). */
    public void logoutSessions(String keycloakId) {
        admin().post().uri(props.adminUrl() + "/users/" + keycloakId + "/logout").retrieve().toBodilessEntity();
    }

    /** Disabling also ends every session, so a locked user's refresh tokens stop working at once. */
    public void setEnabled(String keycloakId, boolean enabled) {
        admin().put().uri(props.adminUrl() + "/users/" + keycloakId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("enabled", enabled))
                .retrieve().toBodilessEntity();
        if (!enabled) {
            admin().post().uri(props.adminUrl() + "/users/" + keycloakId + "/logout").retrieve().toBodilessEntity();
        }
    }

    private RestClient admin() {
        String token = adminToken();
        return http.mutate().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
    }

    private synchronized String adminToken() {
        if (adminToken != null && Instant.now().isBefore(adminTokenExpiry)) {
            return adminToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.adminClientId());
        form.add("client_secret", props.adminClientSecret());
        KeycloakTokens t = http.post().uri(props.tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(KeycloakTokens.class);
        if (t == null || t.accessToken() == null) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, EIamError.AUTH_005);
        }
        adminToken = t.accessToken();
        adminTokenExpiry = Instant.now().plusSeconds(Math.max(0, t.expiresIn() - 30));
        return adminToken;
    }
}
