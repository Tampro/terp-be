package com.terp.erp.employee.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

/** A trainer of the company (FR-TRN-002); not necessarily an employee. */
@Getter
@Setter
@Entity
@Table(name = "trainer")
public class Trainer extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "email", length = 150)
    private String email;

    /** Years of experience. */
    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "branch_id")
    private Long branchId;

    /** A department of branchId. */
    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "expertise", length = 500)
    private String expertise;

    @Column(name = "qualification", length = 500)
    private String qualification;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
