package com.terp.erp.payroll.run;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.client.AttendanceClient;
import com.terp.erp.payroll.client.AttendanceClient.Summary;
import com.terp.erp.payroll.client.EmployeeClient;
import com.terp.erp.payroll.client.EmployeeClient.Employee;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.common.Texts;
import com.terp.erp.payroll.component.SalaryComponent;
import com.terp.erp.payroll.component.SalaryComponentRepository;
import com.terp.erp.payroll.payslip.PayslipRepository;
import com.terp.erp.payroll.run.PayrollRun.EFrequency;
import com.terp.erp.payroll.run.PayrollRun.EStatus;
import com.terp.erp.payroll.salary.EmployeeSalary;
import com.terp.erp.payroll.salary.EmployeeSalaryRepository;
import com.terp.erp.payroll.salary.SalaryMath;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Payroll Runs page and Payroll Run Details (docs/analysis/06 §8.1.2). Only a DRAFT run is edited, processed or
 * deleted (PAY_013); processing snapshots every ACTIVE salary structure of the company with the attendance of the pay
 * period (attendance-service, as the caller) and completes the run in the same request.
 */
@Service
@RequiredArgsConstructor
public class PayrollRunService {
    /** attendance-service summarises at most this many days. */
    public static final int MAX_PERIOD_DAYS = 62;

    private final PayrollRunRepository runs;
    private final PayrollEntryRepository entries;
    private final EmployeeSalaryRepository salaries;
    private final SalaryComponentRepository components;
    private final AttendanceClient attendance;
    private final EmployeeClient employees;
    private final PayslipRepository payslips;
    private final Clock clock;

    public record RunForm(
            @Schema(example = "November 2026", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200)
            String title,
            @Schema(example = "MONTHLY", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EFrequency frequency,
            @Schema(example = "2026-11-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate periodStart,
            @Schema(example = "2026-11-30", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate periodEnd,
            @Schema(example = "2026-12-05", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate payDate,
            @Size(max = 1000) String notes) {
    }

    public record RunDto(String id, String title, EFrequency frequency, LocalDate periodStart, LocalDate periodEnd,
                         LocalDate payDate, String notes, EStatus status, int employeeCount, BigDecimal grossPay,
                         BigDecimal totalDeductions, BigDecimal netPay, Instant processedAt, Instant createdDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record EmployeeInfo(String id, String employeeNo, String fullName, String email, String profileImageUrl) {
    }

    public record LineDto(String componentId, String name, SalaryComponent.EType type,
                          SalaryComponent.ECalculation calculation, BigDecimal value, BigDecimal amount) {
    }

    /** lines only on the details of one entry. */
    public record EntryDto(String id, String runId, EmployeeInfo employee, BigDecimal basicSalary, int workingDays,
                           BigDecimal presentDays, BigDecimal paidLeaveDays, BigDecimal unpaidLeaveDays,
                           BigDecimal lopDays, BigDecimal overtimeHours, BigDecimal totalEarnings,
                           BigDecimal lopDeduction, BigDecimal unpaidLeaveDeduction, BigDecimal overtimeAmount,
                           BigDecimal grossPay, BigDecimal totalDeductions, BigDecimal netPay, List<LineDto> lines) {
    }

    // ------------------------------------------------------------------ runs

    @Transactional(readOnly = true)
    public Page<RunDto> search(Long requestedCompanyId, String keyword, EStatus status, Pageable pageable) {
        return runs.search(Texts.company(requestedCompanyId), status, Texts.kw(keyword), pageable)
                .map(PayrollRunService::dto);
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : runs.countByStatus(Texts.company(requestedCompanyId), Texts.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    @Transactional(readOnly = true)
    public RunDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public RunDto create(Long requestedCompanyId, RunForm form) {
        PayrollRun r = new PayrollRun();
        r.setCompanyId(Texts.company(requestedCompanyId));
        apply(r, form);
        return dto(runs.save(r));
    }

    /** Full replacement of a DRAFT run. */
    @Transactional
    public RunDto update(Long id, RunForm form) {
        PayrollRun r = requireDraft(id);
        apply(r, form);
        return dto(r);
    }

    @Transactional
    public void delete(Long id) {
        requireDraft(id).setDeleted(true);
    }

    /** A DRAFT or COMPLETED run without payslips (PAY_025); the entries stay for the record. */
    @Transactional
    public RunDto cancel(Long id) {
        PayrollRun r = require(id);
        if (r.getStatus() == EStatus.CANCELLED) {
            throw ApiException.badRequest(EPayrollError.PAY_023);
        }
        if (payslips.existsByRunId(r.getId())) {
            throw ApiException.badRequest(EPayrollError.PAY_025);
        }
        r.setStatus(EStatus.CANCELLED);
        return dto(r);
    }

    /**
     * Calculates an entry per ACTIVE salary structure of the company (PayrollMath). An employee attendance-service
     * does not return (no longer in the company) is left out.
     */
    @Transactional
    public RunDto process(Long id) {
        PayrollRun r = requireDraft(id);
        List<EmployeeSalary> active = salaries.findByCompanyIdAndStatusAndDeletedFalse(r.getCompanyId(),
                EmployeeSalary.EStatus.ACTIVE);
        if (active.isEmpty()) {
            throw ApiException.badRequest(EPayrollError.PAY_015);
        }
        Map<Long, Summary> att = attendance.summaries(r.getCompanyId(), r.getPeriodStart(), r.getPeriodEnd(),
                active.stream().map(EmployeeSalary::getEmployeeId).toList());
        Map<Long, SalaryComponent> byId = components.findAllById(active.stream()
                        .flatMap(s -> s.getLines().stream().map(EmployeeSalary.Line::getComponentId)).distinct().toList())
                .stream().collect(Collectors.toMap(SalaryComponent::getId, Function.identity()));
        List<PayrollEntry> made = active.stream().filter(s -> att.containsKey(s.getEmployeeId())).map(s -> {
            PayrollEntry e = new PayrollEntry();
            e.setRunId(r.getId());
            e.setCompanyId(r.getCompanyId());
            e.setEmployeeId(s.getEmployeeId());
            e.setEmployeeName(s.getEmployeeName());
            e.setEmployeeNo(s.getEmployeeNo());
            e.setEmployeeEmail(s.getEmployeeEmail());
            e.setSalaryId(s.getId());
            PayrollMath.fill(e, SalaryMath.totals(s.getBasicSalary(), s.getLines(), byId),
                    att.get(s.getEmployeeId()));
            return e;
        }).toList();
        if (made.isEmpty()) {
            throw ApiException.badRequest(EPayrollError.PAY_015);
        }
        entries.saveAll(made);
        totals(r, made);
        r.setStatus(EStatus.COMPLETED);
        r.setProcessedAt(clock.instant());
        return dto(r);
    }

    // ------------------------------------------------------------------ entries

    /** Employee Payroll Entries, by name; photo and current name from employee-service. */
    @Transactional(readOnly = true)
    public Page<EntryDto> entries(Long runId, Pageable pageable) {
        PayrollRun r = require(runId);
        Page<PayrollEntry> page = entries.findByRunIdOrderByEmployeeNameAsc(r.getId(), pageable);
        Map<Long, Employee> people = page.isEmpty() ? Map.of()
                : employees.byIds(page.map(PayrollEntry::getEmployeeId).toList(), r.getCompanyId());
        return page.map(e -> entryDto(e, people.get(e.getEmployeeId()), false));
    }

    @Transactional(readOnly = true)
    public EntryDto entry(Long runId, Long entryId) {
        return detail(requireEntry(require(runId), entryId));
    }

    /** Removes an employee from a COMPLETED run; the run totals follow. */
    @Transactional
    public RunDto deleteEntry(Long runId, Long entryId) {
        PayrollRun r = require(runId);
        if (r.getStatus() != EStatus.COMPLETED) {
            throw ApiException.badRequest(EPayrollError.PAY_014);
        }
        if (payslips.existsByRunId(r.getId())) {
            throw ApiException.badRequest(EPayrollError.PAY_025);
        }
        entries.delete(requireEntry(r, entryId));
        entries.flush();
        totals(r, entries.findByRunId(r.getId()));
        return dto(r);
    }

    // ------------------------------------------------------------------ helpers

    void apply(PayrollRun r, RunForm f) {
        if (f.periodEnd().isBefore(f.periodStart())
                || ChronoUnit.DAYS.between(f.periodStart(), f.periodEnd()) + 1 > MAX_PERIOD_DAYS) {
            throw ApiException.badRequest(EPayrollError.PAY_016, MAX_PERIOD_DAYS);
        }
        if (f.payDate().isBefore(f.periodStart())) {
            throw ApiException.badRequest(EPayrollError.PAY_017);
        }
        r.setTitle(f.title().trim());
        r.setFrequency(f.frequency());
        r.setPeriodStart(f.periodStart());
        r.setPeriodEnd(f.periodEnd());
        r.setPayDate(f.payDate());
        r.setNotes(Texts.blankToNull(f.notes()));
    }

    static void totals(PayrollRun r, List<PayrollEntry> list) {
        r.setEmployeeCount(list.size());
        r.setGrossPay(list.stream().map(PayrollEntry::getGrossPay).reduce(BigDecimal.ZERO, BigDecimal::add));
        r.setTotalDeductions(list.stream().map(PayrollEntry::getTotalDeductions)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        r.setNetPay(list.stream().map(PayrollEntry::getNetPay).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    static RunDto dto(PayrollRun r) {
        return new RunDto(r.getId().toString(), r.getTitle(), r.getFrequency(), r.getPeriodStart(), r.getPeriodEnd(),
                r.getPayDate(), r.getNotes(), r.getStatus(), r.getEmployeeCount(), r.getGrossPay(),
                r.getTotalDeductions(), r.getNetPay(), r.getProcessedAt(), r.getCreatedDate());
    }

    /** An entry of the caller's company by its own id (Show Payroll). */
    public PayrollEntry requireEntry(Long entryId) {
        PayrollEntry e = entries.findById(entryId).orElseThrow(() -> ApiException.notFound(EPayrollError.PAY_018));
        TenantGuard.assertSameCompany(e.getCompanyId(), EPayrollError.PAY_018);
        return e;
    }

    /** The entry with its lines; photo and current name from employee-service. */
    public EntryDto detail(PayrollEntry e) {
        Employee person = employees.byIds(List.of(e.getEmployeeId()), e.getCompanyId()).get(e.getEmployeeId());
        return entryDto(e, person, true);
    }

    public static EntryDto entryDto(PayrollEntry e, Employee p, boolean withLines) {
        EmployeeInfo info = new EmployeeInfo(e.getEmployeeId().toString(),
                p != null ? p.employeeNo() : e.getEmployeeNo(), p != null ? p.fullName() : e.getEmployeeName(),
                p != null ? p.email() : e.getEmployeeEmail(), p == null ? null : p.profileImageUrl());
        List<LineDto> lines = !withLines ? null : e.getLines().stream()
                .map(l -> new LineDto(l.getComponentId().toString(), l.getName(), l.getType(), l.getCalculation(),
                        l.getValue(), l.getAmount())).toList();
        return new EntryDto(e.getId().toString(), e.getRunId().toString(), info, e.getBasicSalary(),
                e.getWorkingDays(), e.getPresentDays(), e.getPaidLeaveDays(), e.getUnpaidLeaveDays(), e.getLopDays(),
                e.getOvertimeHours(), e.getTotalEarnings(), e.getLopDeduction(), e.getUnpaidLeaveDeduction(),
                e.getOvertimeAmount(), e.getGrossPay(), e.getTotalDeductions(), e.getNetPay(), lines);
    }

    public PayrollRun require(Long id) {
        PayrollRun r = runs.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EPayrollError.PAY_012));
        TenantGuard.assertSameCompany(r.getCompanyId(), EPayrollError.PAY_012);
        return r;
    }

    private PayrollRun requireDraft(Long id) {
        PayrollRun r = require(id);
        if (r.getStatus() != EStatus.DRAFT) {
            throw ApiException.badRequest(EPayrollError.PAY_013);
        }
        return r;
    }

    private PayrollEntry requireEntry(PayrollRun r, Long entryId) {
        return entries.findByIdAndRunId(entryId, r.getId())
                .orElseThrow(() -> ApiException.notFound(EPayrollError.PAY_018));
    }
}
