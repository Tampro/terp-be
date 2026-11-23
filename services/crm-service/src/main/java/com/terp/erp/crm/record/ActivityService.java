package com.terp.erp.crm.record;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.crm.client.AccountingClient;
import com.terp.erp.crm.client.InventoryClient;
import com.terp.erp.crm.client.MediaClient;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.record.CrmEnums.ECallType;
import com.terp.erp.crm.record.CrmEnums.EEntryType;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.record.CrmEnums.ETaskPriority;
import com.terp.erp.crm.record.CrmEnums.ETaskStatus;
import com.terp.erp.crm.setup.Label;
import com.terp.erp.crm.setup.SetupService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.OwnScope;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Everything attached to a lead or a deal (ERP manual 7.2 / 7.3 detail tabs): users, clients, labels, sources and
 * products (links); tasks; calls; discussions; logged emails; files; and the activity log the system writes.
 * Every call starts from the lead or deal, so another company's record answers 404.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {
    private final LeadRepository leads;
    private final DealRepository deals;
    private final CrmLinkRepository links;
    private final CrmTaskRepository tasks;
    private final CrmCallRepository calls;
    private final CrmEntryRepository entries;
    private final SetupService setup;
    private final InventoryClient inventory;
    private final AccountingClient accounting;
    private final MediaClient media;

    /** A lead or deal the caller may touch: its company and pipeline. */
    public record Subject(ESubject type, Long id, Long companyId, Long pipelineId) {
    }

    public record LinkDto(String refId, String name) {
    }

    public record TaskForm(
            @Schema(description = "Name", example = "Send the price list", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String name,
            @Schema(description = "Date", example = "2026-11-28", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate date,
            @Schema(description = "Time", example = "14:30") LocalTime time,
            @Schema(description = "Priority; omit = MEDIUM", example = "HIGH") ETaskPriority priority,
            @Schema(description = "Status; omit = OPEN", example = "OPEN") ETaskStatus status) {
    }

    public record TaskDto(String id, String name, LocalDate date, LocalTime time, ETaskPriority priority,
                          ETaskStatus status) {
        static TaskDto of(CrmTask t) {
            return new TaskDto(t.getId().toString(), t.getName(), t.getTaskDate(), t.getTaskTime(), t.getPriority(),
                    t.getStatus());
        }
    }

    public record CallForm(
            @Schema(description = "Subject", example = "Follow-up call", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String subject,
            @Schema(description = "INBOUND or OUTBOUND", example = "OUTBOUND", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull ECallType callType,
            @Schema(description = "Duration as typed", example = "00:15:00") @Size(max = 20) String duration,
            @Schema(description = "Assignee (account id)", example = "12") Long assigneeUserId,
            @Schema(description = "Description", example = "Asked for a demo") @Size(max = 5000) String description,
            @Schema(description = "Call result", example = "Demo booked") @Size(max = 5000) String callResult) {
    }

    public record CallDto(String id, String subject, ECallType callType, String duration, String assigneeUserId,
                          String description, String callResult, Instant createdDate) {
        static CallDto of(CrmCall c) {
            return new CallDto(c.getId().toString(), c.getSubject(), c.getCallType(), c.getDuration(),
                    c.getAssigneeUserId() == null ? null : c.getAssigneeUserId().toString(), c.getDescription(),
                    c.getCallResult(), c.getCreatedDate());
        }
    }

    public record DiscussionForm(
            @Schema(description = "Comment", example = "Customer prefers a yearly plan", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 5000) String comment) {
    }

    public record EmailForm(
            @Schema(description = "To", example = "buyer@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 255) String to,
            @Schema(description = "Subject", example = "Our offer", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String subject,
            @Schema(description = "Body", example = "<p>Please find our offer.</p>", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 20000) String body) {
    }

    public record FileForm(
            @Schema(description = "Media file id of your company", example = "41", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long mediaFileId) {
    }

    /** Discussion, email, file or activity line. */
    public record EntryDto(String id, EEntryType type, String title, String body, String emailTo, String mediaFileId,
                           String authorName, Instant createdDate) {
        static EntryDto of(CrmEntry e) {
            return new EntryDto(e.getId().toString(), e.getEntryType(), e.getTitle(), e.getBody(), e.getEmailTo(),
                    e.getMediaFileId() == null ? null : e.getMediaFileId().toString(), e.getAuthorName(),
                    e.getCreatedDate());
        }
    }

    public record Activity(List<LinkDto> members, List<LinkDto> clients, List<LinkDto> labels, List<LinkDto> sources,
                           List<LinkDto> products, List<TaskDto> tasks, List<CallDto> calls,
                           List<EntryDto> discussions, List<EntryDto> emails, List<EntryDto> files,
                           List<EntryDto> activity) {
    }

    // ---------- access ----------

    /** Every caller writes (tasks, calls, discussions, emails, files): CRM_MANAGE_OWN reaches only own records. */
    public Subject require(ESubject type, Long id) {
        boolean own = CrmScope.ownManage();
        if (type == ESubject.LEAD) {
            Lead l = leads.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ECrmError.CRM_006));
            TenantGuard.assertSameCompany(l.getCompanyId(), ECrmError.CRM_006);
            if (own) {
                OwnScope.assertMine(CrmScope.mine(l), ECrmError.CRM_006);
            }
            return new Subject(type, l.getId(), l.getCompanyId(), l.getPipelineId());
        }
        Deal d = deals.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ECrmError.CRM_007));
        TenantGuard.assertSameCompany(d.getCompanyId(), ECrmError.CRM_007);
        if (own) {
            OwnScope.assertMine(isMine(d), ECrmError.CRM_007);
        }
        return new Subject(type, d.getId(), d.getCompanyId(), d.getPipelineId());
    }

    /** A deal is mine when I created it or I am one of its MEMBER links. */
    public boolean isMine(Deal d) {
        Long me = OwnScope.userId();
        return java.util.Objects.equals(d.getCreatedBy(), OwnScope.sub())
                || (me != null && links.existsBySubjectTypeAndSubjectIdAndLinkTypeAndRefId(ESubject.DEAL, d.getId(),
                ELinkType.MEMBER, me));
    }

    // ---------- detail ----------

    @Transactional(readOnly = true)
    public Activity activity(Subject s) {
        Map<ELinkType, List<LinkDto>> byType = links.findBySubjectTypeAndSubjectIdOrderByIdAsc(s.type(), s.id()).stream()
                .collect(Collectors.groupingBy(CrmLink::getLinkType,
                        Collectors.mapping(l -> new LinkDto(l.getRefId().toString(), l.getRefName()), Collectors.toList())));
        List<CrmEntry> all = entries.findBySubjectTypeAndSubjectIdAndDeletedFalseOrderByIdDesc(s.type(), s.id());
        return new Activity(byType.getOrDefault(ELinkType.MEMBER, List.of()), byType.getOrDefault(ELinkType.CLIENT, List.of()),
                byType.getOrDefault(ELinkType.LABEL, List.of()), byType.getOrDefault(ELinkType.SOURCE, List.of()),
                byType.getOrDefault(ELinkType.PRODUCT, List.of()),
                tasks.findBySubjectTypeAndSubjectIdAndDeletedFalseOrderByTaskDateAscIdAsc(s.type(), s.id()).stream()
                        .map(TaskDto::of).toList(),
                calls.findBySubjectTypeAndSubjectIdAndDeletedFalseOrderByIdDesc(s.type(), s.id()).stream()
                        .map(CallDto::of).toList(),
                of(all, EEntryType.DISCUSSION), of(all, EEntryType.EMAIL), of(all, EEntryType.FILE),
                of(all, EEntryType.ACTIVITY));
    }

    // ---------- links ----------

    /**
     * Replaces the links of one type. Labels must be of the record's pipeline; sources of its company; products and
     * clients are looked up in inventory- and accounting-service with the caller's token (their 404 relayed).
     */
    // TODO(assumption): member ids are IAM account ids taken as sent — IAM gives other services no user lookup yet.
    @Transactional
    public List<LinkDto> setLinks(Subject s, ELinkType type, List<Long> refIds) {
        Set<Long> ids = new LinkedHashSet<>(refIds == null ? List.of() : refIds);
        List<CrmLink> next = new ArrayList<>();
        for (Long ref : ids) {
            CrmLink l = new CrmLink();
            l.setCompanyId(s.companyId());
            l.setSubjectType(s.type());
            l.setSubjectId(s.id());
            l.setLinkType(type);
            l.setRefId(ref);
            l.setRefName(refName(s, type, ref));
            next.add(l);
        }
        links.deleteLinks(s.type(), s.id(), type);
        links.flush();
        links.saveAll(next);
        log(s, type.name().toLowerCase() + "s updated", next.stream().map(CrmLink::getRefName)
                .filter(java.util.Objects::nonNull).collect(Collectors.joining(", ")));
        return next.stream().map(l -> new LinkDto(l.getRefId().toString(), l.getRefName())).toList();
    }

    private String refName(Subject s, ELinkType type, Long ref) {
        return switch (type) {
            case LABEL -> {
                Label l = setup.requireLabel(ref);
                if (!l.getPipelineId().equals(s.pipelineId())) {
                    throw ApiException.badRequest(ECrmError.CRM_004);
                }
                yield l.getName();
            }
            case SOURCE -> setup.requireSource(ref).getName();
            case PRODUCT -> inventory.item(ref).name();
            case CLIENT -> accounting.customer(ref).companyName();
            case MEMBER -> null;
        };
    }

    /** Links of many records at once, for list pages. */
    public Map<Long, List<CrmLink>> linksOf(ESubject type, java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return links.findBySubjectTypeAndSubjectIdInOrderByIdAsc(type, ids).stream()
                .collect(Collectors.groupingBy(CrmLink::getSubjectId));
    }

    /** [total, done] tasks per record, for list pages. */
    public Map<Long, long[]> taskProgress(ESubject type, java.util.Collection<Long> ids) {
        Map<Long, long[]> m = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] r : tasks.progress(type, ids)) {
                m.put((Long) r[0], new long[]{((Number) r[1]).longValue(), ((Number) r[2]).longValue()});
            }
        }
        return m;
    }

    // ---------- tasks ----------

    @Transactional
    public TaskDto addTask(Subject s, TaskForm f) {
        CrmTask t = new CrmTask();
        t.setCompanyId(s.companyId());
        t.setSubjectType(s.type());
        t.setSubjectId(s.id());
        apply(t, f);
        tasks.save(t);
        log(s, "Task added", t.getName());
        return TaskDto.of(t);
    }

    /** Full replacement. */
    @Transactional
    public TaskDto updateTask(Subject s, Long taskId, TaskForm f) {
        CrmTask t = tasks.findByIdAndSubjectTypeAndSubjectIdAndDeletedFalse(taskId, s.type(), s.id())
                .orElseThrow(() -> ApiException.notFound(ECrmError.CRM_013));
        apply(t, f);
        return TaskDto.of(t);
    }

    @Transactional
    public void deleteTask(Subject s, Long taskId) {
        tasks.findByIdAndSubjectTypeAndSubjectIdAndDeletedFalse(taskId, s.type(), s.id())
                .orElseThrow(() -> ApiException.notFound(ECrmError.CRM_013)).setDeleted(true);
    }

    private static void apply(CrmTask t, TaskForm f) {
        t.setName(f.name().trim());
        t.setTaskDate(f.date());
        t.setTaskTime(f.time());
        t.setPriority(f.priority() == null ? ETaskPriority.MEDIUM : f.priority());
        t.setStatus(f.status() == null ? ETaskStatus.OPEN : f.status());
    }

    // ---------- calls ----------

    @Transactional
    public CallDto addCall(Subject s, CallForm f) {
        CrmCall c = new CrmCall();
        c.setCompanyId(s.companyId());
        c.setSubjectType(s.type());
        c.setSubjectId(s.id());
        apply(c, f);
        calls.save(c);
        log(s, "Call logged", c.getSubject());
        return CallDto.of(c);
    }

    @Transactional
    public CallDto updateCall(Subject s, Long callId, CallForm f) {
        CrmCall c = calls.findByIdAndSubjectTypeAndSubjectIdAndDeletedFalse(callId, s.type(), s.id())
                .orElseThrow(() -> ApiException.notFound(ECrmError.CRM_013));
        apply(c, f);
        return CallDto.of(c);
    }

    @Transactional
    public void deleteCall(Subject s, Long callId) {
        calls.findByIdAndSubjectTypeAndSubjectIdAndDeletedFalse(callId, s.type(), s.id())
                .orElseThrow(() -> ApiException.notFound(ECrmError.CRM_013)).setDeleted(true);
    }

    private static void apply(CrmCall c, CallForm f) {
        c.setSubject(f.subject().trim());
        c.setCallType(f.callType());
        c.setDuration(blank(f.duration()));
        c.setAssigneeUserId(f.assigneeUserId());
        c.setDescription(blank(f.description()));
        c.setCallResult(blank(f.callResult()));
    }

    // ---------- discussions, emails, files ----------

    @Transactional
    public EntryDto addDiscussion(Subject s, DiscussionForm f) {
        return EntryDto.of(entry(s, EEntryType.DISCUSSION, null, f.comment(), null, null));
    }

    /** Logged on the record only: no email is sent (ERP manual lists sending; out of scope here). */
    @Transactional
    public EntryDto addEmail(Subject s, EmailForm f) {
        return EntryDto.of(entry(s, EEntryType.EMAIL, f.subject().trim(), f.body(), f.to().trim(), null));
    }

    @Transactional
    public EntryDto addFile(Subject s, FileForm f) {
        MediaClient.MediaFile file = media.requireCompanyFile(f.mediaFileId(), s.companyId(), ECrmError.CRM_014);
        CrmEntry e = entry(s, EEntryType.FILE, file.name(), null, null, f.mediaFileId());
        log(s, "File attached", file.name());
        return EntryDto.of(e);
    }

    /** Removes a discussion, email or file (activity lines stay). */
    @Transactional
    public void deleteEntry(Subject s, Long entryId) {
        CrmEntry e = entries.findByIdAndSubjectTypeAndSubjectIdAndDeletedFalse(entryId, s.type(), s.id())
                .filter(x -> x.getEntryType() != EEntryType.ACTIVITY)
                .orElseThrow(() -> ApiException.notFound(ECrmError.CRM_013));
        e.setDeleted(true);
    }

    /** One line of the record's activity log. */
    public void log(Subject s, String what, String detail) {
        entry(s, EEntryType.ACTIVITY, what, blank(detail), null, null);
    }

    // ---------- conversion ----------

    /** Copies the chosen activity of a lead to its new deal (ERP manual 7.2 Convert to Deal, "Copy To"). */
    @Transactional
    public void copy(Subject from, Subject to, Set<String> what) {
        for (CrmLink l : links.findBySubjectTypeAndSubjectIdOrderByIdAsc(from.type(), from.id())) {
            boolean wanted = switch (l.getLinkType()) {
                case PRODUCT -> what.contains("PRODUCTS");
                case SOURCE -> what.contains("SOURCES");
                case MEMBER -> what.contains("USERS");
                case LABEL, CLIENT -> false;
            };
            if (wanted) {
                CrmLink c = new CrmLink();
                c.setCompanyId(to.companyId());
                c.setSubjectType(to.type());
                c.setSubjectId(to.id());
                c.setLinkType(l.getLinkType());
                c.setRefId(l.getRefId());
                c.setRefName(l.getRefName());
                links.save(c);
            }
        }
        if (what.contains("TASKS")) {
            for (CrmTask t : tasks.findBySubjectTypeAndSubjectIdAndDeletedFalseOrderByTaskDateAscIdAsc(from.type(), from.id())) {
                CrmTask c = new CrmTask();
                c.setCompanyId(to.companyId());
                c.setSubjectType(to.type());
                c.setSubjectId(to.id());
                c.setName(t.getName());
                c.setTaskDate(t.getTaskDate());
                c.setTaskTime(t.getTaskTime());
                c.setPriority(t.getPriority());
                c.setStatus(t.getStatus());
                tasks.save(c);
            }
        }
        if (what.contains("CALLS")) {
            for (CrmCall k : calls.findBySubjectTypeAndSubjectIdAndDeletedFalseOrderByIdDesc(from.type(), from.id())) {
                CrmCall c = new CrmCall();
                c.setCompanyId(to.companyId());
                c.setSubjectType(to.type());
                c.setSubjectId(to.id());
                c.setSubject(k.getSubject());
                c.setCallType(k.getCallType());
                c.setDuration(k.getDuration());
                c.setAssigneeUserId(k.getAssigneeUserId());
                c.setDescription(k.getDescription());
                c.setCallResult(k.getCallResult());
                calls.save(c);
            }
        }
        for (EEntryType type : List.of(EEntryType.DISCUSSION, EEntryType.EMAIL, EEntryType.FILE)) {
            String flag = switch (type) {
                case DISCUSSION -> "DISCUSSIONS";
                case EMAIL -> "EMAILS";
                default -> "FILES";
            };
            if (!what.contains(flag)) {
                continue;
            }
            for (CrmEntry e : entries.findBySubjectTypeAndSubjectIdAndEntryTypeAndDeletedFalseOrderByIdAsc(
                    from.type(), from.id(), type)) {
                CrmEntry c = new CrmEntry();
                c.setCompanyId(to.companyId());
                c.setSubjectType(to.type());
                c.setSubjectId(to.id());
                c.setEntryType(type);
                c.setTitle(e.getTitle());
                c.setBody(e.getBody());
                c.setEmailTo(e.getEmailTo());
                c.setMediaFileId(e.getMediaFileId());
                c.setAuthorName(e.getAuthorName());
                entries.save(c);
            }
        }
    }

    private CrmEntry entry(Subject s, EEntryType type, String title, String body, String to, Long fileId) {
        CrmEntry e = new CrmEntry();
        e.setCompanyId(s.companyId());
        e.setSubjectType(s.type());
        e.setSubjectId(s.id());
        e.setEntryType(type);
        e.setTitle(title);
        e.setBody(body);
        e.setEmailTo(to);
        e.setMediaFileId(fileId);
        e.setAuthorName(CurrentUser.find().map(p -> p.username()).orElse("system"));
        return entries.save(e);
    }

    private static List<EntryDto> of(List<CrmEntry> all, EEntryType type) {
        return all.stream().filter(e -> e.getEntryType() == type).map(EntryDto::of).toList();
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
