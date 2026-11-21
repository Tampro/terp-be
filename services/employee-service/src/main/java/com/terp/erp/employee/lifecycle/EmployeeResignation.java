package com.terp.erp.employee.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A resignation submitted by an employee (docs/analysis/06 §7.2.5). */
@Getter
@Setter
@Entity
@Table(name = "employee_resignation")
public class EmployeeResignation extends SeparationRecord {
    public enum EStatus { PENDING, APPROVED, REJECTED, COMPLETED }

    @Column(name = "resignation_date", nullable = false)
    private LocalDate resignationDate;

    @Column(name = "last_working_day")
    private LocalDate lastWorkingDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.PENDING;
}
