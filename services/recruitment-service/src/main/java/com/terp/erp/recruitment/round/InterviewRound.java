package com.terp.erp.recruitment.round;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.recruitment.common.BaseAuditableEntity;
import com.terp.erp.recruitment.common.EStatus;

/** A step of the interview process of one job posting (docs/analysis/06 §7.1.15); sequence unique per posting. */
@Getter
@Setter
@Entity
@Table(name = "interview_round")
public class InterviewRound extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "job_posting_id", nullable = false)
    private Long jobPostingId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
