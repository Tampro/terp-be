package com.terp.erp.iam.helpdesk;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * One message in a ticket's conversation. An internal reply is a super admin note the company never sees.
 * Attachments are files of the ticket company's media library (ids only — the files live in media-service).
 */
@Getter
@Setter
@Entity
@Table(name = "helpdesk_reply")
public class HelpdeskReply extends BaseAuditableEntity {
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** Copy of the ticket's company, so a company's replies are found without a join. */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "internal", nullable = false)
    private boolean internal;

    /** Written by the platform (super admin) rather than by the company. */
    @Column(name = "from_platform", nullable = false)
    private boolean fromPlatform;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "helpdesk_reply_attachment", joinColumns = @JoinColumn(name = "reply_id"))
    @OrderColumn(name = "position")
    @Column(name = "media_file_id", nullable = false)
    private List<Long> attachmentFileIds = new ArrayList<>();

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
