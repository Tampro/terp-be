package com.terp.erp.payroll.payslip;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.payroll.client.EmployeeClient;
import com.terp.erp.payroll.client.EmployeeClient.Employee;
import com.terp.erp.payroll.common.EPayrollError;
import com.terp.erp.payroll.common.Texts;
import com.terp.erp.payroll.payslip.Payslip.EStatus;
import com.terp.erp.payroll.run.PayrollEntry;
import com.terp.erp.payroll.run.PayrollEntryRepository;
import com.terp.erp.payroll.run.PayrollRun;
import com.terp.erp.payroll.run.PayrollRunService;
import com.terp.erp.payroll.run.PayrollRunService.EmployeeInfo;
import com.terp.erp.payroll.run.PayrollRunService.EntryDto;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Payslips page (docs/analysis/06 §8.1.1): Generate Payslips on a COMPLETED run makes one payslip per entry that has
 * none yet; downloading the PDF marks it DOWNLOADED. The month tab is the month the pay period starts in.
 * TODO(assumption): month by pay period, not pay date (a November run paid on 5 December shows under November).
 */
@Service
@RequiredArgsConstructor
public class PayslipService {
    private final PayslipRepository payslips;
    private final PayrollEntryRepository entries;
    private final PayrollRunService runs;
    private final EmployeeClient employees;
    private final Clock clock;

    public record PayslipDto(String id, String runId, String runTitle, String entryId, EmployeeInfo employee,
                             LocalDate periodStart, LocalDate periodEnd, LocalDate payDate, BigDecimal netPay,
                             EStatus status, Instant generatedAt, Instant downloadedAt, EntryDto entry) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record GenerateResult(int created, int alreadyGenerated) {
    }

    public record Pdf(String fileName, byte[] bytes) {
    }

    @Transactional
    public GenerateResult generate(Long runId) {
        PayrollRun r = runs.require(runId);
        if (r.getStatus() != PayrollRun.EStatus.COMPLETED) {
            throw ApiException.badRequest(EPayrollError.PAY_024);
        }
        List<PayrollEntry> list = entries.findByRunId(r.getId());
        Set<Long> done = list.isEmpty() ? Set.of()
                : payslips.entriesWithPayslip(list.stream().map(PayrollEntry::getId).toList());
        Instant now = clock.instant();
        List<Payslip> made = list.stream().filter(e -> !done.contains(e.getId())).map(e -> {
            Payslip p = new Payslip();
            p.setCompanyId(r.getCompanyId());
            p.setRunId(r.getId());
            p.setEntryId(e.getId());
            p.setEmployeeId(e.getEmployeeId());
            p.setEmployeeName(e.getEmployeeName());
            p.setEmployeeNo(e.getEmployeeNo());
            p.setEmployeeEmail(e.getEmployeeEmail());
            p.setPeriodYear(r.getPeriodStart().getYear());
            p.setPeriodMonth(r.getPeriodStart().getMonthValue());
            p.setPeriodStart(r.getPeriodStart());
            p.setPeriodEnd(r.getPeriodEnd());
            p.setPayDate(r.getPayDate());
            p.setNetPay(e.getNetPay());
            p.setGeneratedAt(now);
            return p;
        }).toList();
        payslips.saveAll(made);
        return new GenerateResult(made.size(), done.size());
    }

    @Transactional(readOnly = true)
    public Page<PayslipDto> search(Long requestedCompanyId, Integer year, Integer month, Long employeeId,
                                   EStatus status, String keyword, Pageable pageable) {
        Long companyId = Texts.company(requestedCompanyId);
        Page<Payslip> page = payslips.search(companyId, year, month, employeeId, status, Texts.kw(keyword), pageable);
        Map<Long, Employee> people = page.isEmpty() ? Map.of()
                : employees.byIds(page.map(Payslip::getEmployeeId).toList(), companyId);
        return page.map(p -> dto(p, people.get(p.getEmployeeId()), null, null));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, Integer year, Integer month, Long employeeId,
                                     String keyword) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : payslips.countByStatus(Texts.company(requestedCompanyId), year, month, employeeId,
                Texts.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    /** View: the payslip with the full breakdown of its entry. */
    @Transactional(readOnly = true)
    public PayslipDto get(Long id) {
        Payslip p = require(id);
        EntryDto entry = runs.detail(runs.requireEntry(p.getEntryId()));
        return dto(p, null, entry, runs.require(p.getRunId()).getTitle());
    }

    /** Download: the PDF; the payslip becomes DOWNLOADED (first download time kept). */
    @Transactional
    public Pdf pdf(Long id) {
        Payslip p = require(id);
        PayrollEntry e = runs.requireEntry(p.getEntryId());
        PayrollRun r = runs.require(p.getRunId());
        byte[] bytes = PayslipPdf.render(p, e, r);
        if (p.getStatus() == EStatus.GENERATED) {
            p.setStatus(EStatus.DOWNLOADED);
            p.setDownloadedAt(clock.instant());
        }
        String no = p.getEmployeeNo() != null ? p.getEmployeeNo() : p.getEmployeeId().toString();
        return new Pdf("payslip-%s-%d-%02d.pdf".formatted(no, p.getPeriodYear(), p.getPeriodMonth()), bytes);
    }

    private static PayslipDto dto(Payslip p, Employee person, EntryDto entry, String runTitle) {
        EmployeeInfo info = entry != null ? entry.employee() : new EmployeeInfo(p.getEmployeeId().toString(),
                person != null ? person.employeeNo() : p.getEmployeeNo(),
                person != null ? person.fullName() : p.getEmployeeName(),
                person != null ? person.email() : p.getEmployeeEmail(),
                person == null ? null : person.profileImageUrl());
        return new PayslipDto(p.getId().toString(), p.getRunId().toString(), runTitle, p.getEntryId().toString(), info,
                p.getPeriodStart(), p.getPeriodEnd(), p.getPayDate(), p.getNetPay(), p.getStatus(),
                p.getGeneratedAt(), p.getDownloadedAt(), entry);
    }

    private Payslip require(Long id) {
        Payslip p = payslips.findById(id).orElseThrow(() -> ApiException.notFound(EPayrollError.PAY_026));
        TenantGuard.assertSameCompany(p.getCompanyId(), EPayrollError.PAY_026);
        return p;
    }
}
