package com.terp.erp.crm.record;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;
import com.terp.erp.crm.record.CrmEnums.ECallType;
import com.terp.erp.crm.record.CrmEnums.ESubject;

/** A logged call of a lead or deal (ERP manual 7.2 Calls tab: subject, type, duration, assignee, description, result). */
@Getter
@Setter
@Entity
@Table(name = "crm_call")
public class CrmCall extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 10)
    private ESubject subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_type", nullable = false, length = 10)
    private ECallType callType;

    /** As typed, e.g. 00:15:00. */
    @Column(name = "duration", length = 20)
    private String duration;

    @Column(name = "assignee_user_id")
    private Long assigneeUserId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "call_result", columnDefinition = "TEXT")
    private String callResult;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
