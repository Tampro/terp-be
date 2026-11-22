package com.terp.erp.project.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.project.common.BaseAuditableEntity;
import com.terp.erp.project.project.ProjectEnums.EMilestoneStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "prj_milestone")
public class Milestone extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "title", nullable = false, length = 191)
    private String title;

    @Column(name = "cost", precision = 19, scale = 4)
    private BigDecimal cost;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EMilestoneStatus status = EMilestoneStatus.INCOMPLETE;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
