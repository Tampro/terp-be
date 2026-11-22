package com.terp.erp.payroll.overtime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.client.EmployeeClient;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.common.Texts;
import com.terp.erp.payroll.overtime.EmployeeOvertime.EStatus;
import com.terp.erp.payroll.salary.SalaryMath;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Manual overtime of an employee (manual 15 Set salary → Overtimes). It starts PENDING and is decided once:
 * PENDING → APPROVED | REJECTED; edit and delete only while PENDING (PAY_043, manual §20). Overtime a run has paid
 * answers PAY_034 first. TODO(assumption): an approved or rejected overtime is final (before: editable and re-decidable
 * until paid); a wrong approved line is corrected by a new line, not an edit — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class EmployeeOvertimeService {
    private final EmployeeOvertimeRepository overtimes;
    private final EmployeeClient employees;

    public record OvertimeForm(
            @Schema(example = "Year-end closing", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 200) String title,
            @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 2) BigDecimal days,
            @Schema(description = "Hours per day", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 5, fraction = 2) BigDecimal hours,
            @Schema(description = "Amount per hour", example = "150000", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal rate,
            @Schema(example = "2026-11-20", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
            @Schema(example = "2026-11-21", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate endDate,
            @Schema(description = "Omitted = PENDING on create, kept on edit; only APPROVED is paid") EStatus status,
            @Size(max = 1000) String notes) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }

    /** amount = days × hours × rate; paidRunId = the run that paid it. */
    public record OvertimeDto(String id, String employeeId, String title, BigDecimal days, BigDecimal hours,
                              BigDecimal rate, BigDecimal amount, LocalDate startDate, LocalDate endDate,
                              EStatus status, String notes, String paidRunId, Instant createdDate,
                              Instant updatedDate) {
    }

    /**
     * TODO(assumption): days × hours per day × rate per hour, rounded to whole units; paid in full by the first
     * processed run whose period overlaps [start, end] (not prorated across runs) — confirm with BA.
     */
    public static BigDecimal amount(EmployeeOvertime o) {
        return SalaryMath.round(o.getDays().multiply(o.getHours()).multiply(o.getRate()));
    }

    @Transactional(readOnly = true)
    public Page<OvertimeDto> list(Long requestedCompanyId, Long employeeId, EStatus status, Pageable pageable) {
        return overtimes.search(Texts.company(requestedCompanyId), employeeId, status, pageable)
                .map(EmployeeOvertimeService::dto);
    }

    @Transactional(readOnly = true)
    public OvertimeDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public OvertimeDto create(Long requestedCompanyId, Long employeeId, OvertimeForm form) {
        Long companyId = Texts.company(requestedCompanyId);
        EmployeeClient.Employee e = employees.employee(employeeId);
        if (e == null || e.companyId() == null || !Objects.equals(Long.valueOf(e.companyId()), companyId)) {
            throw ApiException.notFound(EPayrollError.PAY_033);
        }
        EmployeeOvertime o = new EmployeeOvertime();
        o.setCompanyId(companyId);
        o.setEmployeeId(employeeId);
        apply(o, form);
        return dto(overtimes.save(o));
    }

    /** Full replacement while PENDING; status omitted = kept (APPROVED / REJECTED here decides it). */
    @Transactional
    public OvertimeDto update(Long id, OvertimeForm form) {
        EmployeeOvertime o = requirePending(id);
        apply(o, form);
        return dto(o);
    }

    /** PENDING → APPROVED | REJECTED (PENDING → PENDING changes nothing); a decided overtime answers PAY_043. */
    @Transactional
    public OvertimeDto changeStatus(Long id, EStatus status) {
        EmployeeOvertime o = requirePending(id);
        o.setStatus(status);
        return dto(o);
    }

    @Transactional
    public void delete(Long id) {
        requirePending(id).setDeleted(true);
    }

    public EmployeeOvertime require(Long id) {
        EmployeeOvertime o = overtimes.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EPayrollError.PAY_033));
        TenantGuard.assertSameCompany(o.getCompanyId(), EPayrollError.PAY_033);
        return o;
    }

    private EmployeeOvertime requireUnpaid(Long id) {
        EmployeeOvertime o = require(id);
        if (o.getPaidRunId() != null) {
            throw ApiException.badRequest(EPayrollError.PAY_034);
        }
        return o;
    }

    private EmployeeOvertime requirePending(Long id) {
        EmployeeOvertime o = requireUnpaid(id);
        if (o.getStatus() != EStatus.PENDING) {
            throw ApiException.badRequest(EPayrollError.PAY_043);
        }
        return o;
    }

    private static void apply(EmployeeOvertime o, OvertimeForm f) {
        if (f.endDate().isBefore(f.startDate())) {
            throw ApiException.badRequest(EPayrollError.PAY_031);
        }
        o.setTitle(f.title().trim());
        o.setDays(f.days());
        o.setHours(f.hours());
        o.setRate(f.rate());
        o.setStartDate(f.startDate());
        o.setEndDate(f.endDate());
        o.setNotes(Texts.blankToNull(f.notes()));
        if (f.status() != null) {
            o.setStatus(f.status());
        }
    }

    static OvertimeDto dto(EmployeeOvertime o) {
        return new OvertimeDto(o.getId().toString(), o.getEmployeeId().toString(), o.getTitle(), o.getDays(),
                o.getHours(), o.getRate(), amount(o), o.getStartDate(), o.getEndDate(), o.getStatus(), o.getNotes(),
                o.getPaidRunId() == null ? null : o.getPaidRunId().toString(), o.getCreatedDate(),
                o.getUpdatedDate());
    }
}
