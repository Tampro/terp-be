package com.terp.erp.payroll.salary;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.terp.erp.payroll.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** The salary structure of an employee: basic salary + components (docs/analysis/06 §8.1.3). */
@Getter
@Setter
@Entity
@Table(name = "employee_salary")
public class EmployeeSalary extends BaseAuditableEntity {
    public enum EStatus { ACTIVE, INACTIVE }

    /** A component of the structure; value null = the component's own value. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class Line {
        @Column(name = "component_id", nullable = false)
        private Long componentId;

        @Column(name = "value", precision = 15, scale = 2)
        private BigDecimal value;
    }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** Copied from employee-service on every save, for search. */
    @Column(name = "employee_name", length = 200)
    private String employeeName;

    @Column(name = "employee_no", length = 50)
    private String employeeNo;

    @Column(name = "employee_email", length = 255)
    private String employeeEmail;

    /** Company currency, per month. */
    @Column(name = "basic_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal basicSalary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "notes", length = 1000)
    private String notes;

    @ElementCollection
    @CollectionTable(name = "employee_salary_component", joinColumns = @JoinColumn(name = "salary_id"))
    private List<Line> lines = new ArrayList<>();

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
