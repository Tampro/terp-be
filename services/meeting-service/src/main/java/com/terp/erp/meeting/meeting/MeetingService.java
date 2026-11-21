package com.terp.erp.meeting.meeting;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.client.EmployeeClient;
import com.terp.erp.meeting.client.EmployeeClient.Employee;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.common.EStatus;
import com.terp.erp.meeting.common.Texts;
import com.terp.erp.meeting.meeting.Meeting.ERecurrence;
import com.terp.erp.meeting.room.MeetingRoom;
import com.terp.erp.meeting.room.MeetingRoomRepository;
import com.terp.erp.meeting.type.MeetingType;
import com.terp.erp.meeting.type.MeetingTypeRepository;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Meetings page (docs/analysis/06 §9.1.1; PO 2026-11-20). A recurring meeting is generated as one row per occurrence
 * (at most {@value #MAX_OCCURRENCES}) sharing a series id; edit, status and delete act on one occurrence. A physical
 * room takes no two live meetings at overlapping times (MTG_011); organizer + attendees fit the room (MTG_012) and are
 * employees of the company (MTG_013).
 */
@Service
@RequiredArgsConstructor
public class MeetingService {
    public static final int MAX_OCCURRENCES = 100;
    /** Attendee photos shown on the list before "+N". */
    static final int LIST_ATTENDEES = 5;

    private final MeetingRepository meetings;
    private final MeetingTypeRepository types;
    private final MeetingRoomRepository rooms;
    private final EmployeeClient employees;
    private final Clock clock;

    public record MeetingForm(
            @Schema(example = "Sprint planning", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @Schema(example = "3") Long meetingTypeId,
            @Schema(example = "5") Long roomId,
            @Schema(example = "2026-11-23", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate meetingDate,
            @Schema(example = "09:00", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalTime startTime,
            @Schema(example = "10:00", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalTime endTime,
            @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long organizerId,
            @Schema(description = "Employee ids; the organizer counts as present") @Size(max = 500)
            Set<Long> attendeeIds,
            @Schema(description = "Create only; omitted = NONE") ERecurrence recurrence,
            @Schema(description = "Create only; required with a recurrence") LocalDate recurrenceEndDate,
            @Size(max = 4000) String agenda) {
    }

    public record Person(String id, String fullName, String email, String profileImageUrl) {
    }

    public record TypeRef(String id, String name, String color) {
    }

    public record RoomRef(String id, String name, MeetingRoom.EType type, String location) {
    }

    /** attendees = all on the details, the first {@value #LIST_ATTENDEES} on the list (attendeeCount = all). */
    public record MeetingDto(String id, String seriesId, String title, String description, String agenda,
                             TypeRef meetingType, RoomRef room, LocalDate meetingDate, LocalTime startTime,
                             LocalTime endTime, long durationMinutes, Person organizer, List<Person> attendees,
                             int attendeeCount, ERecurrence recurrence, LocalDate recurrenceEndDate,
                             Meeting.EStatus status) {
    }

    public record CreateResult(int created, List<MeetingDto> meetings) {
    }

    /** Meeting Summary of a date. */
    public record Summary(LocalDate date, long scheduled, long inProgress, long completed, long cancelled,
                          long total) {
    }

    public record CalendarDay(LocalDate date, long meetings) {
    }

    public record Calendar(int year, int month, LocalDate today, List<CalendarDay> days) {
    }

    // ------------------------------------------------------------------ reads

    /** The meetings of a date (omitted = today), by start time. */
    @Transactional(readOnly = true)
    public List<MeetingDto> ofDay(Long requestedCompanyId, LocalDate date, Meeting.EStatus status) {
        Long companyId = Texts.company(requestedCompanyId);
        List<Meeting> list = meetings.ofDay(companyId, date != null ? date : Texts.today(clock), status);
        return dtos(list, companyId, false);
    }

    /** The meetings of [from, to] (at most 62 days), for the company calendar (docs/analysis/06 §5.2). */
    @Transactional(readOnly = true)
    public List<MeetingDto> inRange(Long requestedCompanyId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw ApiException.badRequest(EMeetingError.MTG_021, MAX_RANGE_DAYS);
        }
        Long companyId = Texts.company(requestedCompanyId);
        return dtos(meetings.inRange(companyId, from, to), companyId, false);
    }

    static final int MAX_RANGE_DAYS = 62;

    @Transactional(readOnly = true)
    public Summary summary(Long requestedCompanyId, LocalDate date) {
        LocalDate d = date != null ? date : Texts.today(clock);
        Map<Meeting.EStatus, Long> by = new EnumMap<>(Meeting.EStatus.class);
        for (Object[] r : meetings.countByStatus(Texts.company(requestedCompanyId), d)) {
            by.put((Meeting.EStatus) r[0], (Long) r[1]);
        }
        long total = by.values().stream().mapToLong(Long::longValue).sum();
        return new Summary(d, by.getOrDefault(Meeting.EStatus.SCHEDULED, 0L),
                by.getOrDefault(Meeting.EStatus.IN_PROGRESS, 0L), by.getOrDefault(Meeting.EStatus.COMPLETED, 0L),
                by.getOrDefault(Meeting.EStatus.CANCELLED, 0L), total);
    }

    /** The mini calendar: dates of the month with meetings (cancelled included). */
    @Transactional(readOnly = true)
    public Calendar calendar(Long requestedCompanyId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<CalendarDay> days = meetings.countByDate(Texts.company(requestedCompanyId), ym.atDay(1),
                ym.atEndOfMonth()).stream().map(r -> new CalendarDay((LocalDate) r[0], (Long) r[1])).toList();
        return new Calendar(year, month, Texts.today(clock), days);
    }

    @Transactional(readOnly = true)
    public MeetingDto get(Long id) {
        Meeting m = require(id);
        return dtos(List.of(m), m.getCompanyId(), true).get(0);
    }

    // ------------------------------------------------------------------ writes

    @Transactional
    public CreateResult create(Long requestedCompanyId, MeetingForm f) {
        Long companyId = Texts.company(requestedCompanyId);
        ERecurrence rec = f.recurrence() == null ? ERecurrence.NONE : f.recurrence();
        List<LocalDate> dates = occurrences(f.meetingDate(), rec, f.recurrenceEndDate());
        Map<Long, Employee> found = people(f, companyId);
        Meeting first = null;
        List<Meeting> made = new ArrayList<>();
        for (LocalDate d : dates) {
            Meeting m = new Meeting();
            m.setCompanyId(companyId);
            m.setRecurrence(rec);
            m.setRecurrenceEndDate(rec == ERecurrence.NONE ? null : f.recurrenceEndDate());
            apply(m, f, d, found);
            m = meetings.save(m);
            if (first == null) {
                first = m;
            }
            if (rec != ERecurrence.NONE) {
                m.setSeriesId(first.getId());
            }
            made.add(m);
        }
        return new CreateResult(made.size(), dtos(made, companyId, false));
    }

    /** Full replacement of one occurrence; the recurrence fields are ignored. */
    @Transactional
    public MeetingDto update(Long id, MeetingForm f) {
        Meeting m = require(id);
        apply(m, f, f.meetingDate(), people(f, m.getCompanyId()));
        return dtos(List.of(m), m.getCompanyId(), true).get(0);
    }

    /** Update Status; back to SCHEDULED / IN_PROGRESS re-checks the room. */
    @Transactional
    public MeetingDto changeStatus(Long id, Meeting.EStatus status) {
        Meeting m = require(id);
        if (m.getStatus() == Meeting.EStatus.CANCELLED && status != Meeting.EStatus.CANCELLED) {
            checkRoom(m.getRoomId(), m, m.getMeetingDate());
        }
        m.setStatus(status);
        return dtos(List.of(m), m.getCompanyId(), true).get(0);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    // ------------------------------------------------------------------ rules

    static List<LocalDate> occurrences(LocalDate start, ERecurrence rec, LocalDate end) {
        if (rec == ERecurrence.NONE) {
            return List.of(start);
        }
        if (end == null || end.isBefore(start)) {
            throw ApiException.badRequest(EMeetingError.MTG_009);
        }
        List<LocalDate> out = new ArrayList<>();
        for (int i = 0; ; i++) {
            LocalDate d = switch (rec) {
                case DAILY -> start.plusDays(i);
                case WEEKLY -> start.plusWeeks(i);
                case MONTHLY -> start.plusMonths(i);
                case NONE -> start;
            };
            if (d.isAfter(end)) {
                return out;
            }
            if (out.size() == MAX_OCCURRENCES) {
                throw ApiException.badRequest(EMeetingError.MTG_010, MAX_OCCURRENCES);
            }
            out.add(d);
        }
    }

    /** Organizer and attendees, checked once per form against the company (MTG_013). */
    private Map<Long, Employee> people(MeetingForm f, Long companyId) {
        Set<Long> people = new LinkedHashSet<>(f.attendeeIds() == null ? Set.of() : f.attendeeIds());
        people.add(f.organizerId());
        Map<Long, Employee> found = employees.byIds(people, companyId);
        if (!found.keySet().containsAll(people)) {
            throw ApiException.badRequest(EMeetingError.MTG_013);
        }
        return found;
    }

    private void apply(Meeting m, MeetingForm f, LocalDate date, Map<Long, Employee> found) {
        if (!f.endTime().isAfter(f.startTime())) {
            throw ApiException.badRequest(EMeetingError.MTG_007);
        }
        Long companyId = m.getCompanyId();
        if (f.meetingTypeId() != null) {
            MeetingType t = types.findByIdAndDeletedFalse(f.meetingTypeId())
                    .filter(x -> x.getCompanyId().equals(companyId))
                    .orElseThrow(() -> ApiException.badRequest(EMeetingError.MTG_002));
            if (t.getStatus() == EStatus.INACTIVE && !t.getId().equals(m.getMeetingTypeId())) {
                throw ApiException.badRequest(EMeetingError.MTG_008, t.getName());
            }
        }
        MeetingRoom room = null;
        if (f.roomId() != null) {
            room = rooms.findByIdAndDeletedFalse(f.roomId()).filter(x -> x.getCompanyId().equals(companyId))
                    .orElseThrow(() -> ApiException.badRequest(EMeetingError.MTG_005));
            if (room.getStatus() == EStatus.INACTIVE && !room.getId().equals(m.getRoomId())) {
                throw ApiException.badRequest(EMeetingError.MTG_008, room.getName());
            }
        }
        Set<Long> attendees = f.attendeeIds() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(f.attendeeIds());
        Set<Long> people = new LinkedHashSet<>(attendees);
        people.add(f.organizerId());
        if (room != null && people.size() > room.getCapacity()) {
            throw ApiException.badRequest(EMeetingError.MTG_012, room.getCapacity());
        }
        m.setTitle(f.title().trim());
        m.setDescription(Texts.blankToNull(f.description()));
        m.setAgenda(Texts.blankToNull(f.agenda()));
        m.setMeetingTypeId(f.meetingTypeId());
        m.setRoomId(f.roomId());
        m.setMeetingDate(date);
        m.setStartTime(f.startTime());
        m.setEndTime(f.endTime());
        Employee organizer = found.get(f.organizerId());
        m.setOrganizerId(f.organizerId());
        m.setOrganizerName(organizer.fullName());
        m.setOrganizerEmail(organizer.email());
        m.getAttendeeIds().clear();
        m.getAttendeeIds().addAll(attendees);
        if (m.getStatus() != Meeting.EStatus.CANCELLED) {
            checkRoom(f.roomId(), m, date);
        }
    }

    /** A physical room is free at the meeting's time (virtual rooms take any number of meetings). */
    private void checkRoom(Long roomId, Meeting m, LocalDate date) {
        if (roomId == null) {
            return;
        }
        MeetingRoom room = rooms.findById(roomId).orElse(null);
        if (room != null && room.getType() == MeetingRoom.EType.PHYSICAL && meetings.roomBusy(roomId, date,
                m.getStartTime(), m.getEndTime(), m.getId() == null ? -1L : m.getId())) {
            throw ApiException.badRequest(EMeetingError.MTG_011, date);
        }
    }

    // ------------------------------------------------------------------ dto

    private List<MeetingDto> dtos(List<Meeting> list, Long companyId, boolean allAttendees) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, MeetingType> byType = byId(types.findAllById(ids(list, Meeting::getMeetingTypeId)),
                MeetingType::getId);
        Map<Long, MeetingRoom> byRoom = byId(rooms.findAllById(ids(list, Meeting::getRoomId)), MeetingRoom::getId);
        Set<Long> people = new HashSet<>();
        for (Meeting m : list) {
            people.add(m.getOrganizerId());
            m.getAttendeeIds().stream().limit(allAttendees ? Long.MAX_VALUE : LIST_ATTENDEES).forEach(people::add);
        }
        Map<Long, Employee> found = employees.byIds(people, companyId);
        return list.stream().map(m -> {
            MeetingType t = m.getMeetingTypeId() == null ? null : byType.get(m.getMeetingTypeId());
            MeetingRoom r = m.getRoomId() == null ? null : byRoom.get(m.getRoomId());
            Employee o = found.get(m.getOrganizerId());
            Person organizer = o != null ? person(o) : new Person(m.getOrganizerId().toString(), m.getOrganizerName(),
                    m.getOrganizerEmail(), null);
            List<Person> attendees = m.getAttendeeIds().stream()
                    .limit(allAttendees ? Long.MAX_VALUE : LIST_ATTENDEES)
                    .map(id -> found.containsKey(id) ? person(found.get(id)) : new Person(id.toString(), null, null,
                            null)).toList();
            return new MeetingDto(m.getId().toString(), m.getSeriesId() == null ? null : m.getSeriesId().toString(),
                    m.getTitle(), m.getDescription(), m.getAgenda(),
                    t == null ? null : new TypeRef(t.getId().toString(), t.getName(), t.getColor()),
                    r == null ? null : new RoomRef(r.getId().toString(), r.getName(), r.getType(), r.getLocation()),
                    m.getMeetingDate(), m.getStartTime(), m.getEndTime(),
                    Duration.between(m.getStartTime(), m.getEndTime()).toMinutes(), organizer, attendees,
                    m.getAttendeeIds().size(), m.getRecurrence(), m.getRecurrenceEndDate(), m.getStatus());
        }).toList();
    }

    private static Person person(Employee e) {
        return new Person(e.id(), e.fullName(), e.email(), e.profileImageUrl());
    }

    private static Set<Long> ids(List<Meeting> list, Function<Meeting, Long> f) {
        return list.stream().map(f).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> Map<Long, T> byId(Collection<T> items, Function<T, Long> id) {
        Map<Long, T> out = new LinkedHashMap<>();
        items.forEach(i -> out.put(id.apply(i), i));
        return out;
    }

    public Meeting require(Long id) {
        Meeting m = meetings.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EMeetingError.MTG_014));
        TenantGuard.assertSameCompany(m.getCompanyId(), EMeetingError.MTG_014);
        return m;
    }
}
