package com.terp.erp.employee.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.time.Instant;
import java.time.LocalDate;

/** What a resignation and a termination share: the notice, the document and the exit interview. */
@Getter
@Setter
@MappedSuperclass
public abstract class SeparationRecord extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** Free text as typed ("30 days", "1 month"). */
    @Column(name = "notice_period", length = 50)
    private String noticePeriod;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "document_media_id")
    private Long documentMediaId;

    @Column(name = "exit_interview_conducted", nullable = false)
    private boolean exitInterviewConducted;

    @Column(name = "exit_interview_date")
    private LocalDate exitInterviewDate;

    @Column(name = "exit_feedback", columnDefinition = "TEXT")
    private String exitFeedback;

    /** When it was completed and the employee terminated. */
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
