package com.terp.erp.employee.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.time.Instant;
import java.time.LocalDate;

/** A file of the media library attached to an employee, with its type, expiry and HR verification. */
@Getter
@Setter
@Entity
@Table(name = "employee_document")
public class EmployeeDocument extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "document_type_id", nullable = false)
    private Long documentTypeId;

    @Column(name = "media_file_id", nullable = false)
    private Long mediaFileId;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "verified_by", length = 64)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;
}
