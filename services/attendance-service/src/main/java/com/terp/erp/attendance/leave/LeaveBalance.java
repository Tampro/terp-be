package com.terp.erp.attendance.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.attendance.common.BaseAuditableEntity;

import java.math.BigDecimal;

/**
 * Leave entitlement of an employee for a leave type and a year, written by Sync (docs/analysis/06 §6.5.2).
 * Total = allocated + carried forward + manual adjustment; Available = Total - used.
 */
@Getter
@Setter
@Entity
@Table(name = "leave_balance")
public class LeaveBalance extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(name = "leave_policy_id")
    private Long leavePolicyId;

    @Column(name = "balance_year", nullable = false)
    private int year;

    @Column(name = "allocated_days", nullable = false, precision = 8, scale = 2)
    private BigDecimal allocatedDays = BigDecimal.ZERO;

    @Column(name = "used_days", nullable = false, precision = 8, scale = 2)
    private BigDecimal usedDays = BigDecimal.ZERO;

    @Column(name = "carried_forward", nullable = false, precision = 8, scale = 2)
    private BigDecimal carriedForward = BigDecimal.ZERO;

    @Column(name = "manual_adjustment", nullable = false, precision = 8, scale = 2)
    private BigDecimal manualAdjustment = BigDecimal.ZERO;

    @Column(name = "adjustment_reason", length = 500)
    private String adjustmentReason;

    public BigDecimal total() {
        return allocatedDays.add(carriedForward).add(manualAdjustment);
    }

    public BigDecimal available() {
        return total().subtract(usedDays);
    }
}
