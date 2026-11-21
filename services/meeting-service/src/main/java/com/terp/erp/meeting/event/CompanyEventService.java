package com.terp.erp.meeting.event;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.client.EmployeeClient;
import com.terp.erp.meeting.client.EmployeeClient.Department;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.common.EStatus;
import com.terp.erp.meeting.common.Texts;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Events page (docs/analysis/06 §9.2.1; PO 2026-11-21). A new event is PENDING; EVENT_APPROVE approves or rejects a
 * pending one (MTG_019); editing an approved or rejected event sends it back to PENDING. Only active event types
 * (MTG_008) and active departments of the company (MTG_020) can be newly chosen. The calendar shows approved events.
 */
@Service
@RequiredArgsConstructor
public class CompanyEventService {
    public static final int MAX_CALENDAR_DAYS = 92;

    private final CompanyEventRepository events;
    private final EventTypeRepository types;
    private final EmployeeClient employees;
    private final Clock clock;

    public record EventForm(
            @Schema(example = "Year-end party", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 200) String title,
            @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long eventTypeId,
            @Schema(example = "2026-12-20", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
            @Schema(example = "2026-12-20", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate endDate,
            @Schema(description = "Omitted with endTime = all day", example = "18:00") LocalTime startTime,
            @Schema(example = "22:00") LocalTime endTime,
            @Size(max = 500) String location,
            @Schema(description = "#RRGGBB; omitted = #3B82F6") @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
            @Schema(description = "Department ids (employee-service), at least one", requiredMode =
                    Schema.RequiredMode.REQUIRED) @NotEmpty @Size(max = 100) Set<@NotNull Long> departmentIds,
            @Size(max = 4000) String description) {
    }

    public record TypeRef(String id, String name) {
    }

    public record DepartmentRef(String id, String name) {
    }

    public record EventDto(String id, String title, TypeRef eventType, LocalDate startDate, LocalDate endDate,
                           LocalTime startTime, LocalTime endTime, String location, String color,
                           List<DepartmentRef> departments, String description, CompanyEvent.EStatus status,
                           String decidedBy, Instant decidedAt, Instant createdDate) {
    }

    public record StatusCounts(long all, Map<CompanyEvent.EStatus, Long> byStatus) {
    }

    public record Filter(String keyword, Long eventTypeId, Long departmentId) {
    }

    @Transactional(readOnly = true)
    public Page<EventDto> search(Long requestedCompanyId, Filter f, CompanyEvent.EStatus status, Pageable pageable) {
        Page<CompanyEvent> page = events.search(Texts.company(requestedCompanyId), f.eventTypeId(), f.departmentId(),
                status, Texts.kw(f.keyword()), pageable);
        Map<Long, EventType> byType = typesOf(page.getContent());
        return page.map(e -> dto(e, byType.get(e.getEventTypeId())));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, Filter f) {
        Map<CompanyEvent.EStatus, Long> by = new EnumMap<>(CompanyEvent.EStatus.class);
        for (CompanyEvent.EStatus s : CompanyEvent.EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : events.countByStatus(Texts.company(requestedCompanyId), f.eventTypeId(), f.departmentId(),
                Texts.kw(f.keyword()))) {
            by.put((CompanyEvent.EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    /** Calendar Integration: approved events overlapping [from, to] (at most {@value #MAX_CALENDAR_DAYS} days). */
    @Transactional(readOnly = true)
    public List<EventDto> calendar(Long requestedCompanyId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_CALENDAR_DAYS) {
            throw ApiException.badRequest(EMeetingError.MTG_021, MAX_CALENDAR_DAYS);
        }
        List<CompanyEvent> list = events.approvedBetween(Texts.company(requestedCompanyId), from, to);
        Map<Long, EventType> byType = typesOf(list);
        return list.stream().map(e -> dto(e, byType.get(e.getEventTypeId()))).toList();
    }

    @Transactional(readOnly = true)
    public EventDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public EventDto create(Long requestedCompanyId, EventForm form) {
        CompanyEvent e = new CompanyEvent();
        e.setCompanyId(Texts.company(requestedCompanyId));
        apply(e, form);
        return detail(events.save(e));
    }

    /** Full replacement; an approved or rejected event goes back to PENDING. */
    @Transactional
    public EventDto update(Long id, EventForm form) {
        CompanyEvent e = require(id);
        apply(e, form);
        e.setStatus(CompanyEvent.EStatus.PENDING);
        e.setDecidedBy(null);
        e.setDecidedAt(null);
        return detail(e);
    }

    @Transactional
    public EventDto decide(Long id, boolean approve) {
        CompanyEvent e = require(id);
        if (e.getStatus() != CompanyEvent.EStatus.PENDING) {
            throw ApiException.badRequest(EMeetingError.MTG_019);
        }
        e.setStatus(approve ? CompanyEvent.EStatus.APPROVED : CompanyEvent.EStatus.REJECTED);
        e.setDecidedBy(CurrentUser.find().map(ErpPrincipal::keycloakId).orElse(null));
        e.setDecidedAt(clock.instant());
        return detail(e);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private void apply(CompanyEvent e, EventForm f) {
        if (f.endDate().isBefore(f.startDate()) || (f.endDate().equals(f.startDate()) && f.startTime() != null
                && f.endTime() != null && !f.endTime().isAfter(f.startTime()))) {
            throw ApiException.badRequest(EMeetingError.MTG_018);
        }
        EventType t = types.findByIdAndDeletedFalse(f.eventTypeId())
                .filter(x -> x.getCompanyId().equals(e.getCompanyId()))
                .orElseThrow(() -> ApiException.badRequest(EMeetingError.MTG_016));
        if (t.getStatus() == EStatus.INACTIVE && !t.getId().equals(e.getEventTypeId())) {
            throw ApiException.badRequest(EMeetingError.MTG_008, t.getName());
        }
        // Departments already on the event may stay even if they became inactive; new ones must be active.
        Map<Long, String> current = e.getDepartments().stream()
                .collect(Collectors.toMap(CompanyEvent.Department::getDepartmentId, CompanyEvent.Department::getName));
        Set<Long> wanted = new LinkedHashSet<>(f.departmentIds());
        Map<Long, Department> active = wanted.stream().allMatch(current::containsKey) ? Map.of()
                : employees.activeDepartments(e.getCompanyId());
        Set<CompanyEvent.Department> deps = new LinkedHashSet<>();
        for (Long id : wanted) {
            Department d = active.get(id);
            if (d != null) {
                deps.add(new CompanyEvent.Department(id, d.name()));
            } else if (current.containsKey(id)) {
                deps.add(new CompanyEvent.Department(id, current.get(id)));
            } else {
                throw ApiException.badRequest(EMeetingError.MTG_020);
            }
        }
        e.setTitle(f.title().trim());
        e.setEventTypeId(t.getId());
        e.setStartDate(f.startDate());
        e.setEndDate(f.endDate());
        e.setStartTime(f.startTime());
        e.setEndTime(f.endTime());
        e.setLocation(Texts.blankToNull(f.location()));
        if (f.color() != null) {
            e.setColor(f.color().toUpperCase(Locale.ROOT));
        }
        e.setDescription(Texts.blankToNull(f.description()));
        e.getDepartments().clear();
        e.getDepartments().addAll(deps);
    }

    private EventDto detail(CompanyEvent e) {
        return dto(e, types.findById(e.getEventTypeId()).orElse(null));
    }

    private Map<Long, EventType> typesOf(List<CompanyEvent> list) {
        return list.isEmpty() ? Map.of() : types.findAllById(list.stream().map(CompanyEvent::getEventTypeId)
                .distinct().toList()).stream().collect(Collectors.toMap(EventType::getId, Function.identity()));
    }

    private static EventDto dto(CompanyEvent e, EventType t) {
        return new EventDto(e.getId().toString(), e.getTitle(),
                t == null ? null : new TypeRef(t.getId().toString(), t.getName()), e.getStartDate(), e.getEndDate(),
                e.getStartTime(), e.getEndTime(), e.getLocation(), e.getColor(),
                e.getDepartments().stream().map(d -> new DepartmentRef(d.getDepartmentId().toString(), d.getName()))
                        .toList(), e.getDescription(), e.getStatus(), e.getDecidedBy(), e.getDecidedAt(),
                e.getCreatedDate());
    }

    public CompanyEvent require(Long id) {
        CompanyEvent e = events.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EMeetingError.MTG_017));
        TenantGuard.assertSameCompany(e.getCompanyId(), EMeetingError.MTG_017);
        return e;
    }
}
