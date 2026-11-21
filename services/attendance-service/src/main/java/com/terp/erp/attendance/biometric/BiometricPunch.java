package com.terp.erp.attendance.biometric;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * One punch as a device sent it. employeeCode is matched to an employee only when an admin views or syncs (a device
 * call carries no user token, so employee-service cannot be asked then).
 */
@Getter
@Setter
@Entity
@Table(name = "biometric_punch")
public class BiometricPunch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    /** Company wall-clock time. */
    @Column(name = "punched_at", nullable = false)
    private LocalDateTime punchedAt;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "attendance_record_id")
    private Long attendanceRecordId;
}
