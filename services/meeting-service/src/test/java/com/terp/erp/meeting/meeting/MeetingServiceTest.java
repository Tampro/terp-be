package com.terp.erp.meeting.meeting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.TestLogin;
import com.terp.erp.meeting.client.EmployeeClient;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.common.EStatus;
import com.terp.erp.meeting.meeting.Meeting.ERecurrence;
import com.terp.erp.meeting.meeting.MeetingService.MeetingForm;
import com.terp.erp.meeting.room.MeetingRoom;
import com.terp.erp.meeting.room.MeetingRoomRepository;
import com.terp.erp.meeting.type.MeetingTypeRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeetingServiceTest {
    private final MeetingRepository meetings = mock(MeetingRepository.class);
    private final MeetingTypeRepository types = mock(MeetingTypeRepository.class);
    private final MeetingRoomRepository rooms = mock(MeetingRoomRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final MeetingService service = new MeetingService(meetings, types, rooms, employees,
            Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneOffset.UTC));
    private final MeetingRoom room = new MeetingRoom();
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "MEETING_MANAGE");
        room.setId(5L);
        room.setCompanyId(10L);
        room.setName("Room A");
        room.setType(MeetingRoom.EType.PHYSICAL);
        room.setCapacity(3);
        when(rooms.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(room));
        when(rooms.findById(5L)).thenReturn(Optional.of(room));
        Map<Long, EmployeeClient.Employee> people = new HashMap<>();
        for (long i = 1; i <= 4; i++) {
            people.put(i, new EmployeeClient.Employee(String.valueOf(i), "10", "EMP" + i, "Test " + i,
                    "t" + i + "@example.invalid", null, "ACTIVE", null, null));
        }
        when(employees.byIds(any(), eq(10L))).thenAnswer(inv -> {
            Map<Long, EmployeeClient.Employee> out = new HashMap<>();
            for (Object id : (java.util.Collection<?>) inv.getArgument(0)) {
                if (people.containsKey((Long) id)) {
                    out.put((Long) id, people.get((Long) id));
                }
            }
            return out;
        });
        when(meetings.save(any())).thenAnswer(i -> {
            Meeting m = i.getArgument(0);
            if (m.getId() == null) {
                m.setId(ids.incrementAndGet());
            }
            return m;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void occurrences() {
        LocalDate jan31 = LocalDate.of(2027, 1, 31);
        assertThat(MeetingService.occurrences(jan31, ERecurrence.MONTHLY, LocalDate.of(2027, 4, 30)))
                .containsExactly(jan31, LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31),
                        LocalDate.of(2027, 4, 30));
        assertThat(MeetingService.occurrences(jan31, ERecurrence.WEEKLY, LocalDate.of(2027, 2, 14))).hasSize(3);
        assertThat(MeetingService.occurrences(jan31, ERecurrence.NONE, null)).containsExactly(jan31);
        assertCode(() -> MeetingService.occurrences(jan31, ERecurrence.DAILY, null), EMeetingError.MTG_009);
        assertCode(() -> MeetingService.occurrences(jan31, ERecurrence.DAILY, LocalDate.of(2027, 6, 1)),
                EMeetingError.MTG_010);
    }

    @Test
    void createSeriesSharesTheFirstId() {
        var res = service.create(null, form(Set.of(2L), ERecurrence.WEEKLY, LocalDate.of(2026, 12, 7)));
        assertThat(res.created()).isEqualTo(3);
        assertThat(res.meetings()).allMatch(m -> "101".equals(m.seriesId()));
        assertThat(res.meetings().get(2).meetingDate()).isEqualTo(LocalDate.of(2026, 12, 7));
        assertThat(res.meetings().get(0).durationMinutes()).isEqualTo(60);
        assertThat(res.meetings().get(0).organizer().fullName()).isEqualTo("Test 1");
        // the employees are fetched once for the whole series (and once for the result)
        verify(employees, times(2)).byIds(any(), eq(10L));
    }

    @Test
    void rules() {
        assertCode(() -> service.create(null, new MeetingForm("X", null, null, 5L, LocalDate.of(2026, 11, 23),
                LocalTime.of(10, 0), LocalTime.of(10, 0), 1L, null, null, null, null)), EMeetingError.MTG_007);
        // organizer + 3 attendees = 4 > capacity 3
        assertCode(() -> service.create(null, form(Set.of(2L, 3L, 4L), null, null)), EMeetingError.MTG_012);
        assertCode(() -> service.create(null, form(Set.of(99L), null, null)), EMeetingError.MTG_013);
        when(meetings.roomBusy(eq(5L), any(), any(), any(), anyLong())).thenReturn(true);
        assertCode(() -> service.create(null, form(Set.of(2L), null, null)), EMeetingError.MTG_011);
        // a virtual room takes overlapping meetings
        room.setType(MeetingRoom.EType.VIRTUAL);
        assertThat(service.create(null, form(Set.of(2L), null, null)).created()).isEqualTo(1);
        room.setStatus(EStatus.INACTIVE);
        assertCode(() -> service.create(null, form(Set.of(2L), null, null)), EMeetingError.MTG_008);
        room.setCompanyId(11L);
        assertCode(() -> service.create(null, form(Set.of(2L), null, null)), EMeetingError.MTG_005);
    }

    @Test
    void summaryAndOtherCompany() {
        when(meetings.countByStatus(10L, LocalDate.of(2026, 11, 20))).thenReturn(List.of(
                new Object[]{Meeting.EStatus.SCHEDULED, 2L}, new Object[]{Meeting.EStatus.CANCELLED, 1L}));
        var s = service.summary(null, null);
        assertThat(s.date()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(s.scheduled()).isEqualTo(2);
        assertThat(s.total()).isEqualTo(3);
        Meeting m = new Meeting();
        m.setId(7L);
        m.setCompanyId(11L);
        when(meetings.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(m));
        assertCode(() -> service.get(7L), EMeetingError.MTG_014);
    }

    private static MeetingForm form(Set<Long> attendees, ERecurrence rec, LocalDate recEnd) {
        return new MeetingForm(" Sprint planning ", null, null, 5L, LocalDate.of(2026, 11, 23), LocalTime.of(9, 0),
                LocalTime.of(10, 0), 1L, attendees, rec, recEnd, "1. Goals");
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EMeetingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
