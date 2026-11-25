package com.terp.erp.iam.user;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.keycloak.KeycloakClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeycloakOrphanJobTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final KeycloakClient keycloak = mock(KeycloakClient.class);
    private final KeycloakOrphanJob job = new KeycloakOrphanJob(jdbc, keycloak);

    private void orphans(String... ids) {
        when(jdbc.queryForList(contains("FROM keycloak_orphan"), eq(String.class))).thenReturn(List.of(ids));
    }

    private void usedBy(String id, int accounts) {
        when(jdbc.queryForObject(contains("FROM user_account"), eq(Integer.class), eq(id))).thenReturn(accounts);
    }

    @Test
    void deletesTheOrphanAndItsEntry() {
        orphans("kc-1");
        usedBy("kc-1", 0);
        job.run();
        verify(keycloak).deleteUser("kc-1");
        verify(jdbc).update(contains("DELETE FROM keycloak_orphan"), eq("kc-1"));
    }

    @Test
    void neverDeletesAnIdAnAccountUses() {
        orphans("kc-1");
        usedBy("kc-1", 1);
        job.run();
        verify(keycloak, never()).deleteUser(anyString());
        verify(jdbc).update(contains("DELETE FROM keycloak_orphan"), eq("kc-1"));
    }

    @Test
    void keycloakStillDownKeepsTheEntryAndStops() {
        orphans("kc-1", "kc-2");
        usedBy("kc-1", 0);
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EIamError.AUTH_005)).when(keycloak).deleteUser("kc-1");
        job.run();
        verify(jdbc).update(contains("attempts = attempts + 1"), eq("kc-1"));
        verify(jdbc, never()).update(contains("DELETE FROM keycloak_orphan"), anyString());
        verify(keycloak, never()).deleteUser("kc-2");
    }
}
