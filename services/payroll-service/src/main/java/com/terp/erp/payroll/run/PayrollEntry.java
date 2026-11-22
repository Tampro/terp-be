package com.terp.erp.payroll.run;

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
import com.terp.erp.payroll.component.SalaryComponent.ECalculation;
import com.terp.erp.payroll.component.SalaryComponent.EType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** One employee of a processed run: the structure and attendance as they were when processed. */
@Getter
@Setter
@Entity
@Table(name = "payroll_entry")
public class PayrollEntry extends BaseAuditableEntity {
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class Line {
        @Column(name = "component_id", nullable = false)
        private Long componentId;

        @Column(name = "name", nullable = false, length = 150)
        private String name;

        @Enumerated(EnumType.STRING)
        @Column(name = "type", nullable = false, length = 10)
        private EType type;

        @Enumerated(EnumType.STRING)
        @Column(name = "calculation", nullable = false, length = 12)
        private ECalculation calculation;

        @Column(name = "value", nullable = false, precision = 15, scale = 2)
        private BigDecimal value;

        @Column(name = "amount", nullable = false, precision = 15, scale = 2)
        private BigDecimal amount;
    }

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_name", length = 200)
    private String employeeName;

    @Column(name = "employee_no", length = 50)
    private String employeeNo;

    @Column(name = "employee_email", length = 255)
    private String employeeEmail;

    @Column(name = "salary_id", nullable = false)
    private Long salaryId;

    @Column(name = "basic_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal basicSalary;

    @Column(name = "working_days", nullable = false)
    private int workingDays;

    @Column(name = "present_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal presentDays;

    @Column(name = "paid_leave_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal paidLeaveDays;

    @Column(name = "unpaid_leave_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal unpaidLeaveDays;

    @Column(name = "lop_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal lopDays;

    @Column(name = "overtime_hours", nullable = false, precision = 7, scale = 2)
    private BigDecimal overtimeHours;

    /** Basic + earning components. */
    @Column(name = "total_earnings", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalEarnings;

    @Column(name = "lop_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal lopDeduction;

    @Column(name = "unpaid_leave_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal unpaidLeaveDeduction;

    @Column(name = "overtime_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal overtimeAmount;

    /** Approved manual overtime paid by this run (days × hours × rate). */
    @Column(name = "manual_overtime_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal manualOvertimeAmount = BigDecimal.ZERO;

    /** Loan installments deducted by this run, each capped at the loan's remaining balance. */
    @Column(name = "loan_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal loanDeduction = BigDecimal.ZERO;

    @Column(name = "gross_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossPay;

    /** Deduction components only (LOP and unpaid leave are already out of the gross pay). */
    @Column(name = "total_deductions", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "net_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal netPay;

    @ElementCollection
    @CollectionTable(name = "payroll_entry_line", joinColumns = @JoinColumn(name = "entry_id"))
    private List<Line> lines = new ArrayList<>();
}
