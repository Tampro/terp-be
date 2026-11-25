package com.terp.erp.iam.user;

import com.terp.erp.iam.keycloak.KeycloakClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Finishes the compensation {@link UserService} could not: a Keycloak user created for a user creation that rolled
 * back while Keycloak was unreachable is recorded in keycloak_orphan and deleted here once Keycloak answers again.
 * An id that a user_account row uses is never deleted (it would lock a real account out) — the row is just dropped.
 * Idempotent: Keycloak answering 404 counts as deleted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "erp.keycloak-orphans.enabled", havingValue = "true", matchIfMissing = true)
public class KeycloakOrphanJob {
    private final JdbcTemplate jdbc;
    private final KeycloakClient keycloak;

    /** Called after the rollback, outside any transaction: the insert commits on its own. */
    public void record(String keycloakId) {
        jdbc.update("INSERT INTO keycloak_orphan (keycloak_id) VALUES (?) ON DUPLICATE KEY UPDATE keycloak_id = keycloak_id",
                keycloakId);
    }

    @Scheduled(cron = "${erp.keycloak-orphans.cron:0 */10 * * * *}")
    @SchedulerLock(name = "iam.keycloak-orphans", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1M")
    public void run() {
        List<String> ids = jdbc.queryForList("SELECT keycloak_id FROM keycloak_orphan ORDER BY id LIMIT 100",
                String.class);
        int deleted = 0;
        for (String id : ids) {
            Integer used = jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE keycloak_id = ?",
                    Integer.class, id);
            if (used != null && used > 0) {
                log.warn("Keycloak orphan {} is used by an ERP account: not deleted, entry dropped", id);
                jdbc.update("DELETE FROM keycloak_orphan WHERE keycloak_id = ?", id);
                continue;
            }
            try {
                keycloak.deleteUser(id);
                jdbc.update("DELETE FROM keycloak_orphan WHERE keycloak_id = ?", id);
                deleted++;
            } catch (RuntimeException ex) {
                jdbc.update("UPDATE keycloak_orphan SET attempts = attempts + 1, last_try_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE keycloak_id = ?", id);
                log.warn("Keycloak orphan {} still not deleted: {}", id, ex.getClass().getSimpleName());
                break; // Keycloak is most likely still down: try again next run
            }
        }
        if (deleted > 0) {
            log.info("Keycloak orphans: {} deleted", deleted);
        }
    }
}
