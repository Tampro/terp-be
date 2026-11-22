package com.terp.erp.project.project;

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
import com.terp.erp.project.common.BaseAuditableEntity;
import com.terp.erp.project.project.ProjectEnums.EProjectStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "prj_project")
public class Project extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 191)
    private String name;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "budget", precision = 19, scale = 4)
    private BigDecimal budget;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EProjectStatus status = EProjectStatus.ONGOING;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    /** Team members: IAM user ids of the same company. */
    @ElementCollection
    @CollectionTable(name = "prj_project_user", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "user_id")
    private Set<Long> userIds = new LinkedHashSet<>();

    /** Clients: accounting-service customer ids. */
    @ElementCollection
    @CollectionTable(name = "prj_project_client", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "customer_id")
    private Set<Long> clientIds = new LinkedHashSet<>();

    /** End date passed and not finished; the day is passed in so the clock stays explicit. */
    public boolean overdue(LocalDate today) {
        return endDate != null && endDate.isBefore(today) && status != EProjectStatus.FINISHED;
    }
}
