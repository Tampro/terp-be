package com.terp.erp.crm.record;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.crm.TestLogin;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.record.CrmEnums.ETaskPriority;
import com.terp.erp.crm.record.CrmEnums.ETaskStatus;
import com.terp.erp.exception.ApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskCalendarServiceTest {
    private static final LocalDate FROM = LocalDate.of(2026, 10, 26);
    private static final LocalDate TO = FROM.plusDays(61);
    private final CrmTaskRepository tasks = mock(CrmTaskRepository.class);
    private final LeadRepository leads = mock(LeadRepository.class);
    private final DealRepository deals = mock(DealRepository.class);
    private final TaskCalendarService service = new TaskCalendarService(tasks, leads, deals);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tasksOfTheCallersCompanyWithTheLeadNameAndDeletedLeadsLeftOut() {
        TestLogin.as(10L, "CRM_VIEW");
        when(tasks.inPeriod(10L, ESubject.LEAD, FROM, TO)).thenReturn(List.of(task(1L, 3L), task(2L, 4L)));
        Lead live = lead(3L, false);
        Lead gone = lead(4L, true);
        when(leads.findAllById(Set.of(3L, 4L))).thenReturn(List.of(live, gone));

        // a company user naming another company still reads their own
        assertThat(service.calendar(99L, ESubject.LEAD, FROM, TO)).singleElement().satisfies(e -> {
            assertThat(e.id()).isEqualTo("1");
            assertThat(e.subjectName()).isEqualTo("Lead 3");
            assertThat(e.subjectType()).isEqualTo(ESubject.LEAD);
        });
    }

    @Test
    void periodIsLimitedAndSuperAdminMustNameACompany() {
        TestLogin.as(10L, "CRM_VIEW");
        assertCode(() -> service.calendar(null, ESubject.DEAL, FROM, TO.plusDays(1)), ECrmError.CRM_021);
        assertCode(() -> service.calendar(null, ESubject.DEAL, TO, FROM), ECrmError.CRM_021);
        TestLogin.superAdmin();
        assertCode(() -> service.calendar(null, ESubject.DEAL, FROM, TO), ECrmError.CRM_001);
    }

    private static CrmTask task(Long id, Long leadId) {
        CrmTask t = new CrmTask();
        t.setId(id);
        t.setCompanyId(10L);
        t.setSubjectType(ESubject.LEAD);
        t.setSubjectId(leadId);
        t.setName("Call back");
        t.setTaskDate(FROM.plusDays(3));
        t.setPriority(ETaskPriority.MEDIUM);
        t.setStatus(ETaskStatus.OPEN);
        return t;
    }

    private static Lead lead(Long id, boolean deleted) {
        Lead l = new Lead();
        l.setId(id);
        l.setCompanyId(10L);
        l.setName("Lead " + id);
        l.setDeleted(deleted);
        return l;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ECrmError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
