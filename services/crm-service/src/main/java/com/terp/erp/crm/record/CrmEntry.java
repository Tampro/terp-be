package com.terp.erp.crm.record;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;
import com.terp.erp.crm.record.CrmEnums.EEntryType;
import com.terp.erp.crm.record.CrmEnums.ESubject;

/**
 * One row of a lead's or deal's history: a discussion comment, a logged email (title = subject, emailTo; not sent),
 * an attached file (mediaFileId, title = file name) or an activity line the system writes (title = what happened).
 */
@Getter
@Setter
@Entity
@Table(name = "crm_entry")
public class CrmEntry extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 10)
    private ESubject subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 12)
    private EEntryType entryType;

    @Column(name = "title")
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "email_to")
    private String emailTo;

    @Column(name = "media_file_id")
    private Long mediaFileId;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
