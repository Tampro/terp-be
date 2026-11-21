package com.terp.erp.employee.training;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/** A training program (docs/analysis/06 §7.4.3). */
@Getter
@Setter
@Entity
@Table(name = "training_program")
public class TrainingProgram extends BaseAuditableEntity {
    public enum EStatus { DRAFT, ACTIVE, COMPLETED, CANCELLED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "training_type_id", nullable = false)
    private Long trainingTypeId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_hours", precision = 7, scale = 1)
    private BigDecimal durationHours;

    /** Company currency. */
    @Column(name = "cost", precision = 15, scale = 2)
    private BigDecimal cost;

    /** Maximum participants; null = no limit. */
    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.DRAFT;

    @Column(name = "prerequisites", columnDefinition = "TEXT")
    private String prerequisites;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "self_enrollment", nullable = false)
    private boolean selfEnrollment;

    /** Media library files. */
    @ElementCollection
    @CollectionTable(name = "training_program_material", joinColumns = @JoinColumn(name = "program_id"))
    @Column(name = "media_id")
    private Set<Long> materialIds = new HashSet<>();

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
