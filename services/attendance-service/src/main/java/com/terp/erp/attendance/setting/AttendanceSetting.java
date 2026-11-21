package com.terp.erp.attendance.setting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.attendance.common.BaseAuditableEntity;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Attendance settings of a company; weeklyOffDays drive Day Off and the working days of the month. */
@Getter
@Setter
@Entity
@Table(name = "attendance_setting")
public class AttendanceSetting extends BaseAuditableEntity {
    public static final Set<DayOfWeek> DEFAULT_OFF_DAYS = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Comma-separated DayOfWeek names, e.g. "SATURDAY,SUNDAY". */
    @Column(name = "weekly_off_days", nullable = false, length = 80)
    private String weeklyOffDays;

    public Set<DayOfWeek> offDays() {
        if (weeklyOffDays == null || weeklyOffDays.isBlank()) {
            return DEFAULT_OFF_DAYS;
        }
        return Arrays.stream(weeklyOffDays.split(",")).map(String::trim).map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    public void setOffDays(Set<DayOfWeek> days) {
        weeklyOffDays = days.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }
}
