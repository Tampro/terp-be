package com.terp.erp.iam.helpdesk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

import java.time.Instant;

/**
 * A support ticket a company sends to the platform (ERP manual 3.3, 11.2). The company opens it and replies; the
 * super admin sees every company's tickets, sets status, priority and category, and may reply internally.
 */
@Getter
@Setter
@Entity
@Table(name = "helpdesk_ticket")
public class HelpdeskTicket extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Shown as #00000012; derived from the id right after the insert. */
    @Column(name = "ticket_number", unique = true, length = 20)
    private String ticketNumber;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EHelpdeskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private EHelpdeskPriority priority;

    /** Set when the ticket becomes RESOLVED, cleared when it is reopened; feeds the "resolved" dashboard line. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
