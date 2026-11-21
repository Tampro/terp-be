package com.terp.erp.employee.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A termination decided by the company (docs/analysis/06 §7.2.6). */
@Getter
@Setter
@Entity
@Table(name = "employee_termination")
public class EmployeeTermination extends SeparationRecord {
    public enum EStatus { PLANNED, IN_PROGRESS, COMPLETED }

    @Column(name = "termination_type_id", nullable = false)
    private Long terminationTypeId;

    @Column(name = "notice_date")
    private LocalDate noticeDate;

    @Column(name = "termination_date", nullable = false)
    private LocalDate terminationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.PLANNED;
}
