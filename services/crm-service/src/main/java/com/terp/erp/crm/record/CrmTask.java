package com.terp.erp.crm.record;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.record.CrmEnums.ETaskPriority;
import com.terp.erp.crm.record.CrmEnums.ETaskStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/** A task of a lead or deal: name, date (required), time, priority, status (ERP manual 7.2 Tasks tab). */
@Getter
@Setter
@Entity
@Table(name = "crm_task")
public class CrmTask extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 10)
    private ESubject subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Column(name = "task_time")
    private LocalTime taskTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private ETaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ETaskStatus status;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
