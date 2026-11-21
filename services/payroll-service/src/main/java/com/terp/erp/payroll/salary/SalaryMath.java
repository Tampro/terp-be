package com.terp.erp.payroll.salary;

import com.terp.erp.payroll.component.SalaryComponent;
import com.terp.erp.payroll.component.SalaryComponent.ECalculation;
import com.terp.erp.payroll.component.SalaryComponent.EType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The monthly amounts of a salary structure (PO, 2026-11-20): a percentage component is that % of the basic salary;
 * amounts are rounded half-up to whole units of the company currency.
 * TODO(assumption): whole units suit VND; a 2-decimal currency would round to cents — confirm per company with BA.
 */
public final class SalaryMath {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record LineAmount(Long componentId, String name, EType type, ECalculation calculation, BigDecimal value,
                             BigDecimal amount) {
    }

    /** earnings = basic + earning components; net = earnings − deductions. */
    public record Totals(BigDecimal basicSalary, List<LineAmount> lines, BigDecimal totalEarnings,
                         BigDecimal totalDeductions, BigDecimal net) {
    }

    private SalaryMath() {
    }

    public static BigDecimal round(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }

    public static Totals totals(BigDecimal basic, List<EmployeeSalary.Line> lines,
                                Map<Long, SalaryComponent> components) {
        BigDecimal earnings = round(basic);
        BigDecimal deductions = BigDecimal.ZERO;
        List<LineAmount> out = new ArrayList<>();
        for (EmployeeSalary.Line l : lines) {
            SalaryComponent c = components.get(l.getComponentId());
            if (c == null) {
                continue;
            }
            BigDecimal value = l.getValue() != null ? l.getValue() : c.getValue();
            BigDecimal amount = round(c.getCalculation() == ECalculation.PERCENTAGE
                    ? basic.multiply(value).divide(HUNDRED, 4, RoundingMode.HALF_UP) : value);
            if (c.getType() == EType.EARNING) {
                earnings = earnings.add(amount);
            } else {
                deductions = deductions.add(amount);
            }
            out.add(new LineAmount(c.getId(), c.getName(), c.getType(), c.getCalculation(), value, amount));
        }
        return new Totals(round(basic), out, earnings, deductions, earnings.subtract(deductions));
    }
}
