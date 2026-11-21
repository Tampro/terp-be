package com.terp.erp.employee.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.time.LocalDate;

/** A complaint filed by an employee, its handling, resolution and follow-up (docs/analysis/06 §7.2.8). */
@Getter
@Setter
@Entity
@Table(name = "employee_complaint")
public class EmployeeComplaint extends BaseAuditableEntity {
    public enum EStatus { SUBMITTED, UNDER_INVESTIGATION, RESOLVED, DISMISSED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Kept even when anonymous: shown only to LIFECYCLE_MANAGE. */
    @Column(name = "complainant_id", nullable = false)
    private Long complainantId;

    /** Null = not against a person (e.g. a policy). */
    @Column(name = "against_id")
    private Long againstId;

    @Column(name = "complaint_type_id", nullable = false)
    private Long complaintTypeId;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "complaint_date", nullable = false)
    private LocalDate complaintDate;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "document_media_id")
    private Long documentMediaId;

    @Column(name = "anonymous", nullable = false)
    private boolean anonymous;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EStatus status = EStatus.SUBMITTED;

    @Column(name = "assigned_to_id")
    private Long assignedToId;

    @Column(name = "resolution_deadline")
    private LocalDate resolutionDeadline;

    @Column(name = "resolution_type_id")
    private Long resolutionTypeId;

    @Column(name = "investigation_notes", columnDefinition = "TEXT")
    private String investigationNotes;

    @Column(name = "resolution_action", columnDefinition = "TEXT")
    private String resolutionAction;

    @Column(name = "resolution_date")
    private LocalDate resolutionDate;

    @Column(name = "follow_up_action", columnDefinition = "TEXT")
    private String followUpAction;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
