package com.terp.erp.meeting.action;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.action.ActionItem.EPriority;
import com.terp.erp.meeting.action.ActionItem.EStatus;
import com.terp.erp.meeting.client.EmployeeClient;
import com.terp.erp.meeting.client.EmployeeClient.Employee;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.common.Texts;
import com.terp.erp.meeting.meeting.Meeting;
import com.terp.erp.meeting.meeting.MeetingRepository;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Action Items page (docs/analysis/06 §9.1.2). The stage follows the progress (0 Not Started, 1–99 In Progress, 100
 * Completed); Overdue = not completed and due before today, shown on its own tab (left out of Not Started / In
 * Progress). The meeting is one of the company's (MTG_014); the assignee an employee of it (MTG_013).
 */
@Service
@RequiredArgsConstructor
public class ActionItemService {
    private final ActionItemRepository items;
    private final MeetingRepository meetings;
    private final EmployeeClient employees;
    private final Clock clock;

    /** The tabs; OVERDUE is computed at read time. */
    public enum ETab { NOT_STARTED, IN_PROGRESS, COMPLETED, OVERDUE }

    public record ItemForm(
            @Schema(example = "7", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long meetingId,
            @Schema(example = "Send the sprint report", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long assigneeId,
            LocalDate dueDate,
            @Schema(description = "Omitted = MEDIUM on create, kept on edit") EPriority priority) {
    }

    public record ProgressForm(
            @Schema(example = "60", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Min(0) @Max(100)
            Integer progress,
            @Size(max = 2000) String progressNotes) {
    }

    public record Person(String id, String fullName, String email, String profileImageUrl) {
    }

    public record MeetingRef(String id, String title, LocalDate meetingDate) {
    }

    /** tab = the status tag: OVERDUE when not completed and past due. */
    public record ItemDto(String id, MeetingRef meeting, String title, String description, Person assignee,
                          LocalDate dueDate, boolean overdue, EPriority priority, int progress, String progressNotes,
                          EStatus status, ETab tab, Instant createdDate, Instant updatedDate) {
    }

    public record TabCounts(long all, long notStarted, long inProgress, long completed, long overdue) {
    }

    public record Filter(String keyword, EPriority priority, Long assigneeId, Long meetingId) {
    }

    @Transactional(readOnly = true)
    public Page<ItemDto> search(Long requestedCompanyId, Filter f, ETab tab, Pageable pageable) {
        Long companyId = Texts.company(requestedCompanyId);
        EStatus stage = tab == null || tab == ETab.OVERDUE ? null : EStatus.valueOf(tab.name());
        Boolean overdue = tab == null || tab == ETab.COMPLETED ? null : tab == ETab.OVERDUE;
        Page<ActionItem> page = items.search(companyId, f.priority(), f.assigneeId(), f.meetingId(),
                Texts.kw(f.keyword()), stage, overdue, today(), pageable);
        return dtos(page, companyId);
    }

    @Transactional(readOnly = true)
    public TabCounts tabCounts(Long requestedCompanyId, Filter f) {
        long notStarted = 0;
        long inProgress = 0;
        long completed = 0;
        long overdue = 0;
        for (Object[] r : items.countTabs(Texts.company(requestedCompanyId), f.priority(), f.assigneeId(),
                f.meetingId(), Texts.kw(f.keyword()), today())) {
            long n = (Long) r[2];
            if (Boolean.TRUE.equals(r[1])) {
                overdue += n;
            } else if (r[0] == EStatus.NOT_STARTED) {
                notStarted += n;
            } else if (r[0] == EStatus.IN_PROGRESS) {
                inProgress += n;
            } else {
                completed += n;
            }
        }
        return new TabCounts(notStarted + inProgress + completed + overdue, notStarted, inProgress, completed, overdue);
    }

    @Transactional(readOnly = true)
    public ItemDto get(Long id) {
        ActionItem a = require(id);
        return dtos(List.of(a), a.getCompanyId()).get(0);
    }

    @Transactional
    public ItemDto create(Long requestedCompanyId, ItemForm form) {
        ActionItem a = new ActionItem();
        a.setCompanyId(Texts.company(requestedCompanyId));
        apply(a, form);
        return dtos(List.of(items.save(a)), a.getCompanyId()).get(0);
    }

    /** Full replacement of the details; progress is kept (Update Progress changes it). */
    @Transactional
    public ItemDto update(Long id, ItemForm form) {
        ActionItem a = require(id);
        apply(a, form);
        return dtos(List.of(a), a.getCompanyId()).get(0);
    }

    /** Update Progress: the stage follows (100 = Completed). */
    @Transactional
    public ItemDto progress(Long id, ProgressForm form) {
        ActionItem a = require(id);
        a.setProgress(form.progress());
        a.setProgressNotes(Texts.blankToNull(form.progressNotes()));
        a.setStatus(EStatus.of(form.progress()));
        return dtos(List.of(a), a.getCompanyId()).get(0);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private void apply(ActionItem a, ItemForm f) {
        Meeting m = meetings.findByIdAndDeletedFalse(f.meetingId())
                .filter(x -> x.getCompanyId().equals(a.getCompanyId()))
                .orElseThrow(() -> ApiException.badRequest(EMeetingError.MTG_014));
        Employee e = employees.byIds(List.of(f.assigneeId()), a.getCompanyId()).get(f.assigneeId());
        if (e == null) {
            throw ApiException.badRequest(EMeetingError.MTG_013);
        }
        a.setMeetingId(m.getId());
        a.setTitle(f.title().trim());
        a.setDescription(Texts.blankToNull(f.description()));
        a.setAssigneeId(f.assigneeId());
        a.setAssigneeName(e.fullName());
        a.setAssigneeEmail(e.email());
        a.setDueDate(f.dueDate());
        if (f.priority() != null) {
            a.setPriority(f.priority());
        }
    }

    private Page<ItemDto> dtos(Page<ActionItem> page, Long companyId) {
        List<ItemDto> list = dtos(page.getContent(), companyId);
        Map<String, ItemDto> byId = list.stream().collect(Collectors.toMap(ItemDto::id, Function.identity()));
        return page.map(a -> byId.get(a.getId().toString()));
    }

    /** Meetings by id (a deleted meeting still names its items); photos from employee-service. */
    private List<ItemDto> dtos(List<ActionItem> list, Long companyId) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, Meeting> byMeeting = meetings.findAllById(list.stream().map(ActionItem::getMeetingId).distinct()
                .toList()).stream().collect(Collectors.toMap(Meeting::getId, Function.identity()));
        Map<Long, Employee> people = employees.byIds(list.stream().map(ActionItem::getAssigneeId).distinct().toList(),
                companyId);
        LocalDate today = today();
        return list.stream().map(a -> {
            Meeting m = byMeeting.get(a.getMeetingId());
            Employee e = people.get(a.getAssigneeId());
            boolean overdue = isOverdue(a, today);
            return new ItemDto(a.getId().toString(),
                    m == null ? null : new MeetingRef(m.getId().toString(), m.getTitle(), m.getMeetingDate()),
                    a.getTitle(), a.getDescription(),
                    e != null ? new Person(e.id(), e.fullName(), e.email(), e.profileImageUrl())
                            : new Person(a.getAssigneeId().toString(), a.getAssigneeName(), a.getAssigneeEmail(), null),
                    a.getDueDate(), overdue, a.getPriority(), a.getProgress(), a.getProgressNotes(), a.getStatus(),
                    overdue ? ETab.OVERDUE : ETab.valueOf(a.getStatus().name()), a.getCreatedDate(),
                    a.getUpdatedDate());
        }).toList();
    }

    static boolean isOverdue(ActionItem a, LocalDate today) {
        return a.getStatus() != EStatus.COMPLETED && a.getDueDate() != null && a.getDueDate().isBefore(today);
    }

    private LocalDate today() {
        return Texts.today(clock);
    }

    public ActionItem require(Long id) {
        ActionItem a = items.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EMeetingError.MTG_015));
        TenantGuard.assertSameCompany(a.getCompanyId(), EMeetingError.MTG_015);
        return a;
    }
}
