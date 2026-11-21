package com.terp.erp.attendance.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.attendance.common.BaseAuditableEntity;
import com.terp.erp.attendance.common.EStatus;

import java.math.BigDecimal;

/**
 * Attendance rules of a company (docs/analysis/06 §6.4.6): grace periods, the half-day threshold and the overtime rate
 * per hour (in the company's currency).
 */
@Getter
@Setter
@Entity
@Table(name = "attendance_policy")
public class AttendancePolicy extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "late_arrival_grace_minutes", nullable = false)
    private int lateArrivalGraceMinutes = 15;

    @Column(name = "early_departure_grace_minutes", nullable = false)
    private int earlyDepartureGraceMinutes = 15;

    /** Fewer worked hours than this make a Half Day. */
    @Column(name = "half_day_threshold_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal halfDayThresholdHours = new BigDecimal("4.00");

    @Column(name = "overtime_rate_per_hour", nullable = false, precision = 12, scale = 2)
    private BigDecimal overtimeRatePerHour = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
