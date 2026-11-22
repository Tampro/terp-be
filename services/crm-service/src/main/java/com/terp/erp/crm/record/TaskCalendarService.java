package com.terp.erp.crm.record;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.record.CrmEnums.ETaskPriority;
import com.terp.erp.crm.record.CrmEnums.ETaskStatus;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lead and deal tasks of a period for the unified calendar (ERP manual 11.3, read by report-service). A task whose
 * lead or deal was deleted is left out, as it is no longer reachable from its record.
 */
@Service
@RequiredArgsConstructor
public class TaskCalendarService {
    /** The unified calendar shows a month with the weeks around it. */
    public static final int MAX_DAYS = 62;

    private final CrmTaskRepository tasks;
    private final LeadRepository leads;
    private final DealRepository deals;

    public record TaskEntry(String id, ESubject subjectType, String subjectId, String subjectName, String name,
                            LocalDate date, LocalTime time, ETaskPriority priority, ETaskStatus status) {
    }

    @Transactional(readOnly = true)
    public List<TaskEntry> calendar(Long companyId, ESubject type, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ApiException.badRequest(ECrmError.CRM_021, MAX_DAYS);
        }
        Long company = TenantGuard.resolveCompany(companyId, ECrmError.CRM_001);
        List<CrmTask> list = tasks.inPeriod(company, type, from, to);
        Set<Long> ids = list.stream().map(CrmTask::getSubjectId).collect(Collectors.toSet());
        Map<Long, String> names = type == ESubject.LEAD
                ? leads.findAllById(ids).stream().filter(l -> !l.isDeleted() && company.equals(l.getCompanyId()))
                .collect(Collectors.toMap(Lead::getId, Lead::getName))
                : deals.findAllById(ids).stream().filter(d -> !d.isDeleted() && company.equals(d.getCompanyId()))
                .collect(Collectors.toMap(Deal::getId, Deal::getName));
        return list.stream().filter(t -> names.containsKey(t.getSubjectId()))
                .map(t -> new TaskEntry(t.getId().toString(), type, t.getSubjectId().toString(),
                        names.get(t.getSubjectId()), t.getName(), t.getTaskDate(), t.getTaskTime(), t.getPriority(),
                        t.getStatus()))
                .toList();
    }
}
