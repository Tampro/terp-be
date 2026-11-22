package com.terp.erp.payroll.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.payroll.common.BaseAuditableEntity;

/**
 * An employee left out of a DRAFT run (FR-ERP-PAY-002 "loại khỏi kỳ"): processing makes no entry for them, so they
 * count in no total.
 */
@Getter
@Setter
@Entity
@Table(name = "payroll_run_exclusion")
public class PayrollRunExclusion extends BaseAuditableEntity {
    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;
}
