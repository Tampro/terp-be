package com.terp.erp.payroll.loan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.catalog.PayrollType;
import com.terp.erp.payroll.catalog.PayrollTypeRepository;
import com.terp.erp.payroll.client.EmployeeClient;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.common.Texts;
import com.terp.erp.payroll.loan.EmployeeLoan.EStatus;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Loans of an employee (manual 15 Set salary → Loans). The installment is given directly or derived from a number of
 * installments (PAY_030). Once a run has deducted from a loan it cannot be deleted, and its amount cannot go below
 * what was repaid (PAY_035).
 * TODO(assumption): one installment per payroll run whatever the run frequency ("monthly installment" in a weekly
 * run is deducted weekly) — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class EmployeeLoanService {
    private final EmployeeLoanRepository loans;
    private final LoanRepaymentRepository repayments;
    private final PayrollTypeRepository types;
    private final EmployeeClient employees;

    public record LoanForm(
            @Schema(example = "Laptop loan", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200)
            String title,
            @Schema(description = "A LOAN type of the company, active", example = "4",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long loanTypeId,
            @Schema(example = "12000000", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Schema(description = "Deducted per payroll run; give this or installments", example = "1000000")
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal monthlyInstallment,
            @Schema(description = "Number of installments; installment = amount / installments rounded up to whole "
                    + "units. Used only when monthlyInstallment is omitted", example = "12") @Min(1) @Max(600)
            Integer installments,
            @Schema(example = "2026-11-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
            @Schema(description = "Omitted = until repaid", example = "2027-10-31") LocalDate endDate,
            @Size(max = 1000) String reason) {
    }

    public record LoanDto(String id, String employeeId, String loanTypeId, String loanTypeName, String title,
                          BigDecimal amount, BigDecimal monthlyInstallment, Integer installments, LocalDate startDate,
                          LocalDate endDate, String reason, EStatus status, BigDecimal remainingBalance,
                          Instant createdDate, Instant updatedDate) {
    }

    @Transactional(readOnly = true)
    public Page<LoanDto> list(Long requestedCompanyId, Long employeeId, Pageable pageable) {
        return loans.findByCompanyIdAndEmployeeIdAndDeletedFalse(Texts.company(requestedCompanyId), employeeId,
                pageable).map(this::dto);
    }

    @Transactional(readOnly = true)
    public LoanDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public LoanDto create(Long requestedCompanyId, Long employeeId, LoanForm form) {
        Long companyId = Texts.company(requestedCompanyId);
        // employee-service answers 404 for another company's employee; this guards a super admin's mix-up too.
        EmployeeClient.Employee e = employees.employee(employeeId);
        if (e == null || e.companyId() == null || !Objects.equals(Long.valueOf(e.companyId()), companyId)) {
            throw ApiException.notFound(EPayrollError.PAY_029);
        }
        EmployeeLoan l = new EmployeeLoan();
        l.setCompanyId(companyId);
        l.setEmployeeId(employeeId);
        apply(l, form, BigDecimal.ZERO);
        return dto(loans.save(l));
    }

    /** Full replacement; the remaining balance follows the new amount less what runs already deducted. */
    @Transactional
    public LoanDto update(Long id, LoanForm form) {
        EmployeeLoan l = require(id);
        apply(l, form, repayments.repaid(l.getId()));
        return dto(l);
    }

    @Transactional
    public void delete(Long id) {
        EmployeeLoan l = require(id);
        if (repayments.existsByLoanId(l.getId())) {
            throw ApiException.badRequest(EPayrollError.PAY_035);
        }
        l.setDeleted(true);
    }

    public EmployeeLoan require(Long id) {
        EmployeeLoan l = loans.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EPayrollError.PAY_029));
        TenantGuard.assertSameCompany(l.getCompanyId(), EPayrollError.PAY_029);
        return l;
    }

    private void apply(EmployeeLoan l, LoanForm f, BigDecimal repaid) {
        if (f.endDate() != null && f.endDate().isBefore(f.startDate())) {
            throw ApiException.badRequest(EPayrollError.PAY_031);
        }
        BigDecimal installment;
        if (f.monthlyInstallment() != null) {
            installment = f.monthlyInstallment();
        } else if (f.installments() != null) {
            installment = f.amount().divide(BigDecimal.valueOf(f.installments()), 0, RoundingMode.CEILING);
        } else {
            throw ApiException.badRequest(EPayrollError.PAY_030);
        }
        PayrollType type = types.findByIdAndDeletedFalse(f.loanTypeId())
                .filter(t -> Objects.equals(t.getCompanyId(), l.getCompanyId()) && t.isActive()
                        && t.getKind() == PayrollType.EKind.LOAN)
                .orElseThrow(() -> ApiException.badRequest(EPayrollError.PAY_032));
        if (f.amount().compareTo(repaid) < 0) {
            throw ApiException.badRequest(EPayrollError.PAY_035);
        }
        l.setTitle(f.title().trim());
        l.setLoanTypeId(type.getId());
        l.setAmount(f.amount());
        l.setInstallmentAmount(installment);
        l.setInstallments(f.monthlyInstallment() != null ? null : f.installments());
        l.setStartDate(f.startDate());
        l.setEndDate(f.endDate());
        l.setReason(Texts.blankToNull(f.reason()));
        l.setRemainingBalance(f.amount().subtract(repaid));
        l.setStatus(l.getRemainingBalance().signum() == 0 ? EStatus.COMPLETED : EStatus.ACTIVE);
    }

    private LoanDto dto(EmployeeLoan l) {
        String typeName = types.findById(l.getLoanTypeId()).map(PayrollType::getName).orElse(null);
        return new LoanDto(l.getId().toString(), l.getEmployeeId().toString(), l.getLoanTypeId().toString(),
                typeName, l.getTitle(), l.getAmount(), l.getInstallmentAmount(), l.getInstallments(),
                l.getStartDate(), l.getEndDate(), l.getReason(), l.getStatus(), l.getRemainingBalance(),
                l.getCreatedDate(), l.getUpdatedDate());
    }
}
