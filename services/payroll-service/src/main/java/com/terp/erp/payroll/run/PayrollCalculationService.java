package com.terp.erp.payroll.run;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.payroll.client.AttendanceClient;
import com.terp.erp.payroll.client.AttendanceClient.Day;
import com.terp.erp.payroll.client.AttendanceClient.Summary;
import com.terp.erp.payroll.common.Texts;
import com.terp.erp.payroll.run.PayrollRun.EStatus;
import com.terp.erp.payroll.run.PayrollRunService.EntryDto;
import com.terp.erp.payroll.run.PayrollRunService.RunDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Show Payroll (docs/analysis/06 §8.1.3): the Payroll Calculation page of one employee in one run, and the runs of
 * the employee for its dropdown.
 */
@Service
@RequiredArgsConstructor
public class PayrollCalculationService {
    private final PayrollEntryRepository entries;
    private final PayrollRunRepository runs;
    private final PayrollRunService runService;
    private final AttendanceClient attendance;

    /** A dropdown option: the entry of the employee in a run. */
    public record RunOption(String entryId, String runId, String runTitle, LocalDate periodStart, LocalDate periodEnd,
                            LocalDate payDate, EStatus runStatus) {
    }

    /**
     * entry = the snapshot (amounts and components as processed); records = the day-by-day attendance of the period
     * as it is now in attendance-service.
     */
    public record Calculation(RunDto run, EntryDto entry, List<Day> records) {
    }

    /** Newest pay period first; deleted runs left out. */
    @Transactional(readOnly = true)
    public List<RunOption> options(Long requestedCompanyId, Long employeeId) {
        Long companyId = Texts.company(requestedCompanyId);
        List<PayrollEntry> mine = entries.findByCompanyIdAndEmployeeId(companyId, employeeId);
        Map<Long, PayrollRun> byId = runs.findAllById(mine.stream().map(PayrollEntry::getRunId).toList()).stream()
                .filter(r -> !r.isDeleted()).collect(Collectors.toMap(PayrollRun::getId, Function.identity()));
        return mine.stream().filter(e -> byId.containsKey(e.getRunId())).map(e -> {
            PayrollRun r = byId.get(e.getRunId());
            return new RunOption(e.getId().toString(), r.getId().toString(), r.getTitle(), r.getPeriodStart(),
                    r.getPeriodEnd(), r.getPayDate(), r.getStatus());
        }).sorted((a, b) -> b.periodStart().compareTo(a.periodStart())).toList();
    }

    @Transactional(readOnly = true)
    public Calculation calculation(Long entryId) {
        PayrollEntry e = runService.requireEntry(entryId);
        PayrollRun r = runService.require(e.getRunId());
        Summary s = attendance.summaries(e.getCompanyId(), r.getPeriodStart(), r.getPeriodEnd(),
                List.of(e.getEmployeeId())).get(e.getEmployeeId());
        return new Calculation(PayrollRunService.dto(r), runService.detail(e), s == null ? List.of() : s.records());
    }
}
