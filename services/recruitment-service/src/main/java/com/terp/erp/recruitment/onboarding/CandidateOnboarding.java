package com.terp.erp.recruitment.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.recruitment.common.BaseAuditableEntity;

import java.time.LocalDate;

/** Onboarding of a new hire (docs/analysis/06 §7.1.5): an employee-service employee following a checklist. */
@Getter
@Setter
@Entity
@Table(name = "candidate_onboarding")
public class CandidateOnboarding extends BaseAuditableEntity {
    public enum EStatus { PENDING, IN_PROGRESS, COMPLETED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_name", length = 200)
    private String employeeName;

    @Column(name = "employee_email", length = 255)
    private String employeeEmail;

    /** The hired candidate this employee came from, when known. */
    @Column(name = "candidate_id")
    private Long candidateId;

    @Column(name = "checklist_id", nullable = false)
    private Long checklistId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "buddy_employee_id")
    private Long buddyEmployeeId;

    @Column(name = "buddy_name", length = 200)
    private String buddyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.PENDING;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
