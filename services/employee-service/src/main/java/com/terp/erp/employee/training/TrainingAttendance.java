package com.terp.erp.employee.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

/** The attendance of one trainee at a session. */
@Getter
@Setter
@Entity
@Table(name = "training_attendance")
public class TrainingAttendance extends BaseAuditableEntity {
    public enum EStatus { PRESENT, ABSENT, EXCUSED }

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status;

    @Column(name = "notes", length = 1000)
    private String notes;
}
