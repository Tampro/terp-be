package com.terp.erp.support.messenger;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Last time each messenger user was active (FE heartbeat, core parity §18). One row per user, upserted, so it works
 * with several instances; a user counts as online while the last heartbeat is younger than {@link #ONLINE_FOR}.
 */
@Component
@RequiredArgsConstructor
class ChatPresence {
    static final java.time.Duration ONLINE_FOR = java.time.Duration.ofMinutes(2);

    private final JdbcTemplate jdbc;

    void touch(long companyId, long userId, Instant now) {
        jdbc.update("INSERT INTO chat_presence (company_id, user_id, last_seen_at) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE last_seen_at = VALUES(last_seen_at)", companyId, userId,
                Timestamp.from(now));
    }

    /** Last seen of these users of the company; users never seen are absent. */
    Map<Long, Instant> lastSeen(long companyId, Collection<Long> userIds) {
        Map<Long, Instant> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        String in = String.join(",", java.util.Collections.nCopies(userIds.size(), "?"));
        Object[] args = new Object[userIds.size() + 1];
        args[0] = companyId;
        int i = 1;
        for (Long id : userIds) {
            args[i++] = id;
        }
        jdbc.query("SELECT user_id, last_seen_at FROM chat_presence WHERE company_id = ? AND user_id IN (" + in + ")",
                rs -> {
                    out.put(rs.getLong(1), rs.getTimestamp(2).toInstant());
                }, args);
        return out;
    }
}
