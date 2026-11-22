package com.terp.erp.support.ticket;

import com.terp.erp.support.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A support ticket (reference tickets). Statuses and account types are the reference's; a CLOSED ticket takes no
 * reply (ERP manual 18). note is internal and never leaves the admin API.
 */
@Getter
@Setter
@Entity
@Table(name = "ticket")
public class Ticket extends BaseAuditableEntity {
    /** Reference StoreSupportTicketRequest: In Progress, On Hold, Closed (the update request also lists "open"). */
    public enum EStatus { IN_PROGRESS, ON_HOLD, CLOSED }

    /** Reference: custom, staff, client, vendor; the portal always creates CUSTOM. */
    public enum EAccountType { CUSTOM, STAFF, CLIENT, VENDOR }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "ticket_number", nullable = false, length = 20)
    private String ticketNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10)
    private EAccountType accountType;

    @Column(name = "name", nullable = false)
    private String name;

    /** Stored lower-cased: the portal looks a ticket up by number + email. */
    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EStatus status;

    @Column(name = "description", columnDefinition = "MEDIUMTEXT")
    private String description;

    @Column(name = "attachments", columnDefinition = "TEXT")
    private String attachments;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "tracking_token_hash", nullable = false, length = 64)
    private String trackingTokenHash;

    @Column(name = "from_portal", nullable = false)
    private boolean fromPortal;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
