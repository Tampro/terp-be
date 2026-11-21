package com.terp.erp.attendance.shift;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.attendance.common.BaseAuditableEntity;
import com.terp.erp.attendance.common.EStatus;

import java.time.Duration;
import java.time.LocalTime;

/**
 * A work shift of a company (docs/analysis/06 §6.4.5). A night shift may end on the next day: end time at or before
 * the start time means "next day". Times are wall-clock times of the company.
 */
@Getter
@Setter
@Entity
@Table(name = "shift")
public class Shift extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_duration_minutes", nullable = false)
    private int breakDurationMinutes;

    @Column(name = "break_start_time")
    private LocalTime breakStartTime;

    @Column(name = "break_end_time")
    private LocalTime breakEndTime;

    /** Minutes after the start before an arrival counts as late. */
    @Column(name = "grace_period_minutes", nullable = false)
    private int gracePeriodMinutes;

    @Column(name = "night_shift", nullable = false)
    private boolean nightShift;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    /** Start to end, over midnight when the end is not after the start. */
    public static long spanMinutes(LocalTime start, LocalTime end) {
        long m = Duration.between(start, end).toMinutes();
        return m > 0 ? m : m + 24 * 60;
    }

    /** Working Time of the card: shift span minus the break. */
    public long workingMinutes() {
        return spanMinutes(startTime, endTime) - breakDurationMinutes;
    }
}
