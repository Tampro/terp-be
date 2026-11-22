package com.terp.erp.support.messenger;

import com.terp.erp.support.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One direct message between two IAM users of the same company (reference Chatify ch_messages: from_id, to_id, body
 * up to 5000, attachment, seen). The body is private chat: never log it.
 */
@Getter
@Setter
@Entity
@Table(name = "chat_message")
public class ChatMessage extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(name = "body", length = 5000)
    private String body;

    @Column(name = "attachment_file_id")
    private Long attachmentFileId;

    @Column(name = "attachment_name")
    private String attachmentName;

    @Column(name = "seen", nullable = false)
    private boolean seen;

    @Column(name = "seen_at")
    private Instant seenAt;

    @Override
    public String toString() {
        // No body: entities end up in logs and exception messages.
        return "ChatMessage[" + getId() + "]";
    }
}
