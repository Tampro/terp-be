package com.terp.erp.meeting.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.TestLogin;
import com.terp.erp.meeting.client.EmployeeClient;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.common.EStatus;
import com.terp.erp.meeting.event.CompanyEventService.EventForm;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyEventServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-21T02:00:00Z");
    private final CompanyEventRepository events = mock(CompanyEventRepository.class);
    private final EventTypeRepository types = mock(EventTypeRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final CompanyEventService service = new CompanyEventService(events, types, employees,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final EventType party = new EventType();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "EVENT_MANAGE", "EVENT_APPROVE");
        party.setId(3L);
        party.setCompanyId(10L);
        party.setName("Party");
        when(types.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(party));
        when(types.findById(3L)).thenReturn(Optional.of(party));
        when(employees.activeDepartments(10L)).thenReturn(Map.of(
                20L, new EmployeeClient.Department("20", "Engineering", "1"),
                21L, new EmployeeClient.Department("21", "Sales", "1")));
        when(events.save(any())).thenAnswer(i -> {
            CompanyEvent e = i.getArgument(0);
            e.setId(9L);
            return e;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPendingThenApproveAndEditBackToPending() {
        var e = service.create(null, form(Set.of(20L, 21L), LocalTime.of(18, 0), LocalTime.of(22, 0)));
        assertThat(e.status()).isEqualTo(CompanyEvent.EStatus.PENDING);
        assertThat(e.departments()).extracting(CompanyEventService.DepartmentRef::name)
                .containsExactlyInAnyOrder("Engineering", "Sales");
        assertThat(e.color()).isEqualTo("#22C55E");

        CompanyEvent stored = new CompanyEvent();
        stored.setId(9L);
        stored.setCompanyId(10L);
        stored.setEventTypeId(3L);
        stored.getDepartments().add(new CompanyEvent.Department(30L, "Old Dept"));
        when(events.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(stored));
        var approved = service.decide(9L, true);
        assertThat(approved.status()).isEqualTo(CompanyEvent.EStatus.APPROVED);
        assertThat(approved.decidedAt()).isEqualTo(NOW);
        assertCode(() -> service.decide(9L, false), EMeetingError.MTG_019);

        // the department already on the event may stay although it is no longer active
        party.setStatus(EStatus.INACTIVE);
        var edited = service.update(9L, form(Set.of(30L), null, null));
        assertThat(edited.status()).isEqualTo(CompanyEvent.EStatus.PENDING);
        assertThat(edited.decidedAt()).isNull();
        assertThat(edited.departments()).extracting(CompanyEventService.DepartmentRef::name).containsExactly("Old Dept");
        verify(employees, never()).activeDepartments(11L);
    }

    @Test
    void rules() {
        assertCode(() -> service.create(null, form(Set.of(20L), LocalTime.of(18, 0), LocalTime.of(17, 0))),
                EMeetingError.MTG_018);
        assertCode(() -> service.create(null, new EventForm("X", 3L, LocalDate.of(2026, 12, 20),
                LocalDate.of(2026, 12, 19), null, null, null, null, Set.of(20L), null)), EMeetingError.MTG_018);
        assertCode(() -> service.create(null, form(Set.of(99L), null, null)), EMeetingError.MTG_020);
        party.setStatus(EStatus.INACTIVE);
        assertCode(() -> service.create(null, form(Set.of(20L), null, null)), EMeetingError.MTG_008);
        party.setCompanyId(11L);
        assertCode(() -> service.create(null, form(Set.of(20L), null, null)), EMeetingError.MTG_016);
        assertCode(() -> service.calendar(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1)),
                EMeetingError.MTG_021);
    }

    private static EventForm form(Set<Long> departments, LocalTime start, LocalTime end) {
        return new EventForm(" Year-end party ", 3L, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 20), start,
                end, "Rooftop", "#22c55e", departments, null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EMeetingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
