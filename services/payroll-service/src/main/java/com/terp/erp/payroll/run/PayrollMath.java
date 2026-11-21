package com.terp.erp.payroll.run;

import com.terp.erp.payroll.client.AttendanceClient.Summary;
import com.terp.erp.payroll.salary.SalaryMath;
import com.terp.erp.payroll.salary.SalaryMath.Totals;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Payroll Calculation Reference (docs/analysis/06 §8.1.2, PO 2026-11-20):
 * <pre>
 * Total earnings   = basic + earning components
 * LOP deduction    = basic / working days × LOP days
 * Unpaid leave     = basic / working days × unpaid leave days
 * Gross pay        = total earnings − LOP deduction − unpaid leave deduction + overtime amount
 * Net pay          = gross pay − deduction components
 * </pre>
 * Overtime amount is the attendance records' own amount (hours × the attendance policy's rate per hour, §6.4).
 * Amounts are rounded half-up to whole units like {@link SalaryMath}.
 * TODO(assumption): the components are not prorated by attendance, and a negative gross or net pay is kept as
 * computed (shown, not hidden) — confirm both with BA.
 */
public final class PayrollMath {
    private PayrollMath() {
    }

    public static void fill(PayrollEntry e, Totals t, Summary a) {
        BigDecimal basic = t.basicSalary();
        e.setBasicSalary(basic);
        e.setWorkingDays(a.workingDays());
        e.setPresentDays(nz(a.presentDays()));
        e.setPaidLeaveDays(nz(a.paidLeaveDays()));
        e.setUnpaidLeaveDays(nz(a.unpaidLeaveDays()));
        e.setLopDays(nz(a.lopDays()));
        e.setOvertimeHours(nz(a.overtimeHours()));
        e.setTotalEarnings(t.totalEarnings());
        e.setLopDeduction(perDay(basic, a.workingDays(), e.getLopDays()));
        e.setUnpaidLeaveDeduction(perDay(basic, a.workingDays(), e.getUnpaidLeaveDays()));
        e.setOvertimeAmount(SalaryMath.round(nz(a.overtimeAmount())));
        e.setGrossPay(t.totalEarnings().subtract(e.getLopDeduction()).subtract(e.getUnpaidLeaveDeduction())
                .add(e.getOvertimeAmount()));
        e.setTotalDeductions(t.totalDeductions());
        e.setNetPay(e.getGrossPay().subtract(t.totalDeductions()));
        e.getLines().clear();
        t.lines().forEach(l -> e.getLines().add(new PayrollEntry.Line(l.componentId(), l.name(), l.type(),
                l.calculation(), l.value(), l.amount())));
    }

    /** No working day in the period: nothing to deduct per day. */
    static BigDecimal perDay(BigDecimal basic, int workingDays, BigDecimal days) {
        if (workingDays <= 0 || days.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return SalaryMath.round(basic.multiply(days).divide(BigDecimal.valueOf(workingDays), 4, RoundingMode.HALF_UP));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
