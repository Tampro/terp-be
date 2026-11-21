package com.terp.erp.attendance.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.attendance.common.BaseAuditableEntity;
import com.terp.erp.attendance.common.EStatus;

/** A kind of leave of a company (docs/analysis/06 §6.5.3): days allocated per year, paid or unpaid, calendar colour. */
@Getter
@Setter
@Entity
@Table(name = "leave_type")
public class LeaveType extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "max_days_per_year", nullable = false)
    private int maxDaysPerYear;

    @Column(name = "paid", nullable = false)
    private boolean paid = true;

    @Column(name = "color", nullable = false, length = 7)
    private String color = "#3B82F6";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
