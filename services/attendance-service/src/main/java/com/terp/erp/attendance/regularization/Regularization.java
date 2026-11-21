package com.terp.erp.attendance.regularization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.attendance.common.BaseAuditableEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A request to correct the clock times of an attendance record (docs/analysis/06 §6.4.4). original* are the record's
 * times when the request was made; a requested time left empty keeps the record's time on approval.
 */
@Getter
@Setter
@Entity
@Table(name = "attendance_regularization")
public class Regularization extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "attendance_record_id", nullable = false)
    private Long attendanceRecordId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "requested_clock_in")
    private LocalTime requestedClockIn;

    @Column(name = "requested_clock_out")
    private LocalTime requestedClockOut;

    @Column(name = "original_clock_in")
    private LocalTime originalClockIn;

    @Column(name = "original_clock_out")
    private LocalTime originalClockOut;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ERegularizationStatus status = ERegularizationStatus.PENDING;

    @Column(name = "manager_comments", length = 1000)
    private String managerComments;

    /** Keycloak subject of who approved or rejected. */
    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
