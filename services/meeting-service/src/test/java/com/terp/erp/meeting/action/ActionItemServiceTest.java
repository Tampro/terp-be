package com.terp.erp.meeting.action;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.TestLogin;
import com.terp.erp.meeting.action.ActionItem.EStatus;
import com.terp.erp.meeting.action.ActionItemService.ETab;
import com.terp.erp.meeting.action.ActionItemService.Filter;
import com.terp.erp.meeting.action.ActionItemService.ItemForm;
import com.terp.erp.meeting.action.ActionItemService.ProgressForm;
import com.terp.erp.meeting.client.EmployeeClient;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.meeting.Meeting;
import com.terp.erp.meeting.meeting.MeetingRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionItemServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private final ActionItemRepository items = mock(ActionItemRepository.class);
    private final MeetingRepository meetings = mock(MeetingRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final ActionItemService service = new ActionItemService(items, meetings, employees,
            Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneOffset.UTC));
    private final Meeting meeting = new Meeting();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "MEETING_MANAGE");
        meeting.setId(7L);
        meeting.setCompanyId(10L);
        meeting.setTitle("Sprint planning");
        when(meetings.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(meeting));
        when(meetings.findAllById(any())).thenReturn(List.of(meeting));
        when(employees.byIds(any(), eq(10L))).thenReturn(Map.of(12L, new EmployeeClient.Employee("12", "10",
                "EMP12", "Test Twelve", "t12@example.invalid", null, "ACTIVE", null, null)));
        when(items.save(any())).thenAnswer(i -> {
            ActionItem a = i.getArgument(0);
            a.setId(3L);
            return a;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stagesFollowProgressAndOverdueIsComputed() {
        assertThat(EStatus.of(0)).isEqualTo(EStatus.NOT_STARTED);
        assertThat(EStatus.of(1)).isEqualTo(EStatus.IN_PROGRESS);
        assertThat(EStatus.of(100)).isEqualTo(EStatus.COMPLETED);
        var created = service.create(null, new ItemForm(7L, " Send report ", null, 12L, TODAY.minusDays(1), null));
        assertThat(created.priority()).isEqualTo(ActionItem.EPriority.MEDIUM);
        assertThat(created.overdue()).isTrue();
        assertThat(created.tab()).isEqualTo(ETab.OVERDUE);
        assertThat(created.meeting().title()).isEqualTo("Sprint planning");

        ActionItem a = new ActionItem();
        a.setId(3L);
        a.setCompanyId(10L);
        a.setMeetingId(7L);
        a.setAssigneeId(12L);
        a.setDueDate(TODAY.minusDays(1));
        when(items.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(a));
        var done = service.progress(3L, new ProgressForm(100, " Sent "));
        assertThat(done.status()).isEqualTo(EStatus.COMPLETED);
        assertThat(done.overdue()).isFalse();
        assertThat(done.progressNotes()).isEqualTo("Sent");
        assertThat(service.progress(3L, new ProgressForm(40, null)).tab()).isEqualTo(ETab.OVERDUE);
    }

    @Test
    void tabsMapToQueryParameters() {
        Page<ActionItem> empty = new PageImpl<>(List.of());
        when(items.search(any(), any(), any(), any(), anyString(), any(), any(), any(), any())).thenReturn(empty);
        Filter f = new Filter(null, null, null, null);
        service.search(null, f, ETab.OVERDUE, Pageable.unpaged());
        verify(items).search(eq(10L), isNull(), isNull(), isNull(), eq(""), isNull(), eq(true), eq(TODAY), any());
        service.search(null, f, ETab.IN_PROGRESS, Pageable.unpaged());
        verify(items).search(eq(10L), isNull(), isNull(), isNull(), eq(""), eq(EStatus.IN_PROGRESS), eq(false),
                eq(TODAY), any());
        service.search(null, f, ETab.COMPLETED, Pageable.unpaged());
        verify(items).search(eq(10L), isNull(), isNull(), isNull(), eq(""), eq(EStatus.COMPLETED), isNull(),
                eq(TODAY), any());
        when(items.countTabs(eq(10L), any(), any(), any(), anyString(), eq(TODAY))).thenReturn(List.of(
                new Object[]{EStatus.NOT_STARTED, false, 2L}, new Object[]{EStatus.NOT_STARTED, true, 1L},
                new Object[]{EStatus.IN_PROGRESS, true, 1L}, new Object[]{EStatus.COMPLETED, false, 3L}));
        var c = service.tabCounts(null, f);
        assertThat(c.all()).isEqualTo(7);
        assertThat(c.notStarted()).isEqualTo(2);
        assertThat(c.overdue()).isEqualTo(2);
        assertThat(c.completed()).isEqualTo(3);
    }

    @Test
    void formRules() {
        meeting.setCompanyId(11L);
        assertCode(() -> service.create(null, new ItemForm(7L, "X", null, 12L, null, null)), EMeetingError.MTG_014);
        meeting.setCompanyId(10L);
        assertCode(() -> service.create(null, new ItemForm(7L, "X", null, 99L, null, null)), EMeetingError.MTG_013);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EMeetingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
