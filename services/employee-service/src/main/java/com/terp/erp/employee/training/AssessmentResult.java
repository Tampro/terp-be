package com.terp.erp.employee.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The result of one employee training in an assessment; passed = score ≥ the assessment's passing score. */
@Getter
@Setter
@Entity
@Table(name = "assessment_result")
public class AssessmentResult extends BaseAuditableEntity {
    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Column(name = "employee_training_id", nullable = false)
    private Long employeeTrainingId;

    /** %. */
    @Column(name = "score", nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "assessed_date")
    private LocalDate assessedDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
