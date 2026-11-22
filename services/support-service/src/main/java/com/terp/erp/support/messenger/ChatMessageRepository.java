package com.terp.erp.support.messenger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Every query is bound to the caller (me) and their company: no message of another pair is reachable. */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    @Query("select m from ChatMessage m where m.companyId = :company"
            + " and ((m.fromUserId = :me and m.toUserId = :peer) or (m.fromUserId = :peer and m.toUserId = :me))"
            + " order by m.id desc")
    Page<ChatMessage> between(@Param("company") Long companyId, @Param("me") Long me, @Param("peer") Long peer,
                              Pageable pageable);

    /** [peer id, id of the last message] per conversation of me, most recent first. */
    @Query(value = "SELECT x.peer, MAX(x.id) AS last_id FROM (SELECT CASE WHEN from_user_id = :me THEN to_user_id"
            + " ELSE from_user_id END AS peer, id FROM chat_message WHERE company_id = :company"
            + " AND (from_user_id = :me OR to_user_id = :me)) x GROUP BY x.peer ORDER BY last_id DESC",
            countQuery = "SELECT COUNT(DISTINCT CASE WHEN from_user_id = :me THEN to_user_id ELSE from_user_id END)"
                    + " FROM chat_message WHERE company_id = :company AND (from_user_id = :me OR to_user_id = :me)",
            nativeQuery = true)
    Page<Object[]> conversations(@Param("company") Long companyId, @Param("me") Long me, Pageable pageable);

    /** [sender id, unread count] of messages to me from the given peers. */
    @Query("select m.fromUserId, count(m) from ChatMessage m where m.companyId = :company and m.toUserId = :me"
            + " and m.seen = false and m.fromUserId in :peers group by m.fromUserId")
    List<Object[]> unreadBySender(@Param("company") Long companyId, @Param("me") Long me,
                                  @Param("peers") Collection<Long> peers);

    long countByCompanyIdAndToUserIdAndSeenFalse(Long companyId, Long toUserId);

    @Modifying
    @Query("update ChatMessage m set m.seen = true, m.seenAt = :now where m.companyId = :company"
            + " and m.toUserId = :me and m.fromUserId = :peer and m.seen = false")
    int markRead(@Param("company") Long companyId, @Param("me") Long me, @Param("peer") Long peer,
                 @Param("now") Instant now);
}
