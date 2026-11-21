package com.terp.erp.document.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.document.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The contract of an employee (docs/analysis/06 §9.4.2). */
@Getter
@Setter
@Entity
@Table(name = "employee_contract")
public class EmployeeContract extends BaseAuditableEntity {
    /** PENDING = Pending Approval. As set; an ACTIVE contract past its end date reads as EXPIRED. */
    public enum EStatus { DRAFT, PENDING, ACTIVE, EXPIRED, TERMINATED, RENEWED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "contract_number", length = 30)
    private String contractNumber;

    @Column(name = "contract_type_id", nullable = false)
    private Long contractTypeId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_name", length = 200)
    private String employeeName;

    @Column(name = "employee_no", length = 50)
    private String employeeNo;

    @Column(name = "employee_email", length = 255)
    private String employeeEmail;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** null = open-ended. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "basic_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.DRAFT;

    /** The generated contract PDF (media library file), when one was attached. */
    @Column(name = "document_file_id")
    private Long documentFileId;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
