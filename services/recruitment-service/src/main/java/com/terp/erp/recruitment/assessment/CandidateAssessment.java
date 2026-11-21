package com.terp.erp.recruitment.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.recruitment.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An assessment taken by a candidate (docs/analysis/06 §7.1.6); the outcome is chosen by hand. */
@Getter
@Setter
@Entity
@Table(name = "candidate_assessment")
public class CandidateAssessment extends BaseAuditableEntity {
    public enum EStatus { PENDING, PASS, FAIL }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "score", nullable = false, precision = 9, scale = 2)
    private BigDecimal score;

    @Column(name = "max_score", nullable = false, precision = 9, scale = 2)
    private BigDecimal maxScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 8)
    private EStatus status = EStatus.PENDING;

    @Column(name = "conducted_by_employee_id")
    private Long conductedByEmployeeId;

    @Column(name = "conducted_by_name", length = 200)
    private String conductedByName;

    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate;

    @Column(name = "comments", length = 5000)
    private String comments;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
