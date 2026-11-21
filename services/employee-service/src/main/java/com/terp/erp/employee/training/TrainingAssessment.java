package com.terp.erp.employee.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.math.BigDecimal;

/** An assessment of a training program (docs/analysis/06 §7.4.4). */
@Getter
@Setter
@Entity
@Table(name = "training_assessment")
public class TrainingAssessment extends BaseAuditableEntity {
    public enum EType { QUIZ, PRACTICAL, PRESENTATION }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 12)
    private EType type;

    /** %. */
    @Column(name = "passing_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal passingScore;

    @Column(name = "criteria", columnDefinition = "TEXT")
    private String criteria;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
