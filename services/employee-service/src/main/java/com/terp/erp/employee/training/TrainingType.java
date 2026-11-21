package com.terp.erp.employee.training;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.util.HashSet;
import java.util.Set;

/** A training type of the company, optionally tied to a branch and its departments (docs/analysis/06 §7.4.5). */
@Getter
@Setter
@Entity
@Table(name = "training_type")
public class TrainingType extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    /** Null = every branch. */
    @Column(name = "branch_id")
    private Long branchId;

    /** Assign Departments; within the branch when one is set. */
    @ElementCollection
    @CollectionTable(name = "training_type_department", joinColumns = @JoinColumn(name = "training_type_id"))
    @Column(name = "department_id")
    private Set<Long> departmentIds = new HashSet<>();

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
