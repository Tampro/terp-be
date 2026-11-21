package com.terp.erp.document.hr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.document.common.BaseAuditableEntity;

import java.time.Instant;
import java.time.LocalDate;

/** A document an employee must acknowledge (docs/analysis/06 §9.4.3). */
@Getter
@Setter
@Entity
@Table(name = "document_acknowledgment")
public class Acknowledgment extends BaseAuditableEntity {
    /** Stored; OVERDUE (pending past the due date) is computed. */
    public enum EStatus { PENDING, ACKNOWLEDGED, EXEMPTED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** The employee's IAM user id at assignment: lets them acknowledge it themself. */
    @Column(name = "employee_user_id")
    private Long employeeUserId;

    @Column(name = "employee_name", length = 200)
    private String employeeName;

    @Column(name = "employee_email", length = 255)
    private String employeeEmail;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "note", length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.PENDING;

    /** Keycloak sub and username of who assigned it (Assigned By). */
    @Column(name = "assigned_by", length = 64)
    private String assignedBy;

    @Column(name = "assigned_by_name", length = 200)
    private String assignedByName;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by", length = 64)
    private String acknowledgedBy;

    @Column(name = "acknowledge_note", length = 1000)
    private String acknowledgeNote;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
