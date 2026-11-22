package com.terp.erp.support.ticket;

import com.terp.erp.support.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One message of a ticket's conversation (reference support_ticket_conversions; sender admin = STAFF, user = CUSTOMER). */
@Getter
@Setter
@Entity
@Table(name = "ticket_reply")
public class TicketReply extends BaseAuditableEntity {
    public enum ESender { STAFF, CUSTOMER }

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 10)
    private ESender senderType;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "description", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String description;

    @Column(name = "attachments", columnDefinition = "TEXT")
    private String attachments;
}
