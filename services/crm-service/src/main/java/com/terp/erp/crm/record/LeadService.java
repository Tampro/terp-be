package com.terp.erp.crm.record;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.crm.client.AccountingClient;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.record.ActivityService.Activity;
import com.terp.erp.crm.record.ActivityService.LinkDto;
import com.terp.erp.crm.record.ActivityService.Subject;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.setup.Pipeline;
import com.terp.erp.crm.setup.SetupEntities.EStageKind;
import com.terp.erp.crm.setup.SetupService;
import com.terp.erp.crm.setup.Stage;
import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.security.OwnScope;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Leads (ERP manual 7.2): list, kanban by stage, detail, convert once into a deal. */
@Service
@RequiredArgsConstructor
public class LeadService {
    static final String EVENT_ASSIGNED = "crm.lead.assigned";
    static final String EVENT_MOVED = "crm.lead.moved";
    static final String EVENT_CREATED = "crm.lead.created";
    static final String EVENT_CONVERTED = "crm.lead.converted";
    /** What a conversion can copy, as named in the manual's "Copy To" list. */
    static final Set<String> COPYABLE = Set.of("PRODUCTS", "SOURCES", "FILES", "DISCUSSIONS", "NOTES", "CALLS",
            "EMAILS", "TASKS", "USERS");

    private final LeadRepository leads;
    private final DealRepository deals;
    private final SetupService setup;
    private final ActivityService activity;
    private final AccountingClient accounting;
    private final OutboxPublisher outbox;

    public record LeadForm(
            @Schema(description = "Pipeline id (it decides the company)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long pipelineId,
            @Schema(description = "Lead stage of the pipeline; omit = its first lead stage", example = "2") Long stageId,
            @Schema(description = "Name", example = "Test Prospect", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String name,
            @Schema(description = "Email", example = "prospect@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 255) String email,
            @Schema(description = "Subject", example = "Wants a 20-seat plan", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String subject,
            @Schema(description = "Assigned user (account id)", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long ownerUserId,
            @Schema(description = "Phone", example = "+84 90 000 0000") @Size(max = 30) String phone,
            @Schema(description = "Follow-up date", example = "2026-11-30") LocalDate followUpDate,
            @Schema(description = "Notes (rich text)", example = "<p>Met at the expo</p>") @Size(max = 20000) String notes) {
    }

    public record MoveForm(
            @Schema(description = "Target stage (same pipeline, same kind)", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long stageId,
            @Schema(description = "Position in the column; omit = last", example = "1") Integer position) {
    }

    public record LinksForm(
            @Schema(description = "Ids replacing the current ones (empty = none)", example = "[\"3\", \"4\"]")
            List<@NotNull Long> ids) {
    }

    public record NewClient(
            @Schema(description = "Customer company name", example = "Test Prospect Co", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String companyName,
            @Schema(description = "Contact person; omit = the lead's name", example = "Test Prospect") @Size(max = 255) String contactPerson,
            @Schema(description = "Email; omit = the lead's email", example = "prospect@example.com") @Email @Size(max = 255) String email,
            @Schema(description = "Mobile", example = "+84 90 000 0000") @Size(max = 30) String mobile) {
    }

    public record ConvertForm(
            @Schema(description = "Deal name; omit = the lead's subject", example = "20-seat plan") @Size(max = 255) String dealName,
            @Schema(description = "Price", example = "12000000") @DecimalMin("0") BigDecimal price,
            @Schema(description = "Existing client: an accounting customer id") Long customerId,
            @Schema(description = "New client: created as an accounting customer") @Valid NewClient newClient,
            @Schema(description = "What to copy: PRODUCTS, SOURCES, FILES, DISCUSSIONS, NOTES, CALLS, EMAILS, TASKS, USERS",
                    example = "[\"PRODUCTS\", \"TASKS\", \"NOTES\"]") Set<@NotBlank String> copy) {
    }

    public record LeadRow(String id, String pipelineId, String stageId, String stageName, String name, String email,
                          String subject, String phone, LocalDate followUpDate,
                          @Schema(description = "Follow-up date passed") boolean followUpOverdue, String ownerUserId,
                          long tasksTotal, long tasksDone, List<LinkDto> labels, int position, String convertedDealId,
                          Instant createdDate) {
    }

    public record LeadDetail(LeadRow lead, String notes, Activity activity) {
    }

    public record Column(String stageId, String name, List<LeadRow> leads) {
    }

    @Transactional(readOnly = true)
    public Page<LeadRow> search(Long companyId, Long pipelineId, Long stageId, Long ownerUserId, LocalDate followFrom,
                                LocalDate followTo, String keyword, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(companyId, ECrmError.CRM_001);
        Page<Lead> page = CrmScope.ownView()
                ? leads.searchOwn(company, pipelineId, stageId, ownerUserId, followFrom, followTo, like(keyword),
                OwnScope.userId(), OwnScope.sub(), pageable)
                : leads.search(company, pipelineId, stageId, ownerUserId, followFrom, followTo, like(keyword), pageable);
        List<LeadRow> rows = rows(page.getContent());
        return page.map(l -> rows.get(page.getContent().indexOf(l)));
    }

    /** The lead board of a pipeline: one column per lead stage, cards by position. */
    @Transactional(readOnly = true)
    public List<Column> board(Long pipelineId) {
        Pipeline p = setup.requirePipeline(pipelineId);
        boolean own = CrmScope.ownView();
        List<LeadRow> rows = rows(leads.findByCompanyIdAndPipelineIdAndDeletedFalseOrderByPositionAscIdAsc(
                p.getCompanyId(), p.getId()).stream().filter(l -> !own || CrmScope.mine(l)).toList());
        List<Column> out = new ArrayList<>();
        for (Stage s : setup.stagesOf(p.getId(), EStageKind.LEAD)) {
            out.add(new Column(s.getId().toString(), s.getName(),
                    rows.stream().filter(r -> r.stageId().equals(s.getId().toString())).toList()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public LeadDetail get(Long id) {
        Lead l = require(id);
        if (CrmScope.ownView()) {
            OwnScope.assertMine(CrmScope.mine(l), ECrmError.CRM_006);
        }
        return detail(l);
    }

    private LeadDetail detail(Lead l) {
        return new LeadDetail(rows(List.of(l)).get(0), l.getNotes(), activity.activity(subject(l)));
    }

    @Transactional
    public LeadDetail create(LeadForm f) {
        // The pipeline decides the company (requirePipeline already refuses another company's pipeline).
        Pipeline p = setup.requirePipeline(f.pipelineId());
        Stage s = f.stageId() == null ? setup.firstStage(p.getId(), EStageKind.LEAD)
                : setup.requireStageOf(f.stageId(), p.getId(), EStageKind.LEAD);
        Lead l = new Lead();
        l.setCompanyId(p.getCompanyId());
        l.setPipelineId(p.getId());
        l.setStageId(s.getId());
        l.setPosition(leads.maxPosition(s.getId()) + 1);
        apply(l, f);
        leads.save(l);
        activity.log(subject(l), "Lead created", l.getName());
        outbox.publish(l.getCompanyId(), EVENT_CREATED, "LEAD", l.getId(), Payload.of("leadId", l.getId(),
                "title", l.getName(), "pipelineId", l.getPipelineId(), "name", l.getName(), "subject", l.getSubject(),
                "stage", s.getName(), "ownerUserId", l.getOwnerUserId()));
        assigned(l);
        return detail(l);
    }

    /** Full replacement of the lead's fields; the pipeline and stage change only through move. */
    @Transactional
    public LeadDetail update(Long id, LeadForm f) {
        Lead l = requireManaged(id);
        Long owner = l.getOwnerUserId();
        apply(l, f);
        if (!java.util.Objects.equals(owner, l.getOwnerUserId())) {
            assigned(l);
        }
        return detail(l);
    }

    @Transactional
    public void delete(Long id) {
        requireManaged(id).setDeleted(true);
    }

    /** Kanban drag: another lead stage of the same pipeline; cards after the position shift down. */
    @Transactional
    public LeadDetail move(Long id, MoveForm f) {
        Lead l = requireManaged(id);
        Stage to = setup.requireStageOf(f.stageId(), l.getPipelineId(), EStageKind.LEAD);
        String fromName = setup.stageNames(List.of(l.getStageId())).get(l.getStageId());
        List<Lead> column = leads.findByCompanyIdAndPipelineIdAndDeletedFalseOrderByPositionAscIdAsc(
                l.getCompanyId(), l.getPipelineId()).stream()
                .filter(x -> x.getStageId().equals(to.getId()) && !x.getId().equals(l.getId())).toList();
        int pos = f.position() == null ? column.size() + 1 : Math.max(1, Math.min(f.position(), column.size() + 1));
        int i = 1;
        for (Lead x : column) {
            if (i == pos) {
                i++;
            }
            x.setPosition(i++);
        }
        l.setStageId(to.getId());
        l.setPosition(pos);
        if (!to.getName().equals(fromName)) {
            activity.log(subject(l), "Moved", fromName + " → " + to.getName());
            // Notifications LEAD_MOVED (email to the owner, webhook LEAD_MOVED).
            outbox.publish(l.getCompanyId(), EVENT_MOVED, "LEAD", l.getId(), Payload.of("leadId", l.getId(),
                    "pipelineId", l.getPipelineId(), "name", l.getName(), "subject", l.getSubject(),
                    "fromStage", fromName, "toStage", to.getName(), "ownerUserId", l.getOwnerUserId(),
                    "recipientUserIds", Payload.ids(java.util.Collections.singletonList(l.getOwnerUserId()))));
        }
        return detail(l);
    }

    @Transactional
    public List<LinkDto> setLinks(Long id, ELinkType type, LinksForm f) {
        if (type == ELinkType.CLIENT) {
            throw ApiException.badRequest(ECrmError.CRM_013);
        }
        return activity.setLinks(subject(requireManaged(id)), type, f.ids());
    }

    /**
     * Lead → deal, once (CRM_011). The client is an existing accounting customer or a new one created there with the
     * caller's token (CUSTOMER_MANAGE needed there). The deal starts at the pipeline's first deal stage.
     */
    @Transactional
    public Map<String, String> convert(Long id, ConvertForm f) {
        Lead l = requireManaged(id);
        if (l.getConvertedDealId() != null) {
            throw ApiException.badRequest(ECrmError.CRM_011, l.getConvertedDealId().toString());
        }
        if ((f.customerId() == null) == (f.newClient() == null)) {
            throw ApiException.badRequest(ECrmError.CRM_018);
        }
        Stage first = setup.firstStage(l.getPipelineId(), EStageKind.DEAL);
        AccountingClient.Customer client = f.customerId() != null ? accounting.customer(f.customerId())
                : accounting.create(f.newClient().companyName().trim(),
                blankOr(f.newClient().contactPerson(), l.getName()), blankOr(f.newClient().email(), l.getEmail()),
                f.newClient().mobile() == null ? l.getPhone() : f.newClient().mobile());
        Set<String> copy = new HashSet<>(f.copy() == null ? Set.of() : f.copy());
        copy.retainAll(COPYABLE);
        Deal d = new Deal();
        d.setCompanyId(l.getCompanyId());
        d.setPipelineId(l.getPipelineId());
        d.setStageId(first.getId());
        d.setPosition(deals.maxPosition(first.getId()) + 1);
        d.setName(blankOr(f.dealName(), l.getSubject()));
        d.setPrice(f.price() == null ? BigDecimal.ZERO : f.price());
        d.setPhone(l.getPhone());
        d.setNotes(copy.contains("NOTES") ? l.getNotes() : null);
        d.setFromLeadId(l.getId());
        deals.save(d);
        Subject from = subject(l);
        Subject to = new Subject(ESubject.DEAL, d.getId(), d.getCompanyId(), d.getPipelineId());
        activity.copy(from, to, copy);
        activity.setLinks(to, ELinkType.CLIENT, List.of(Long.valueOf(client.id())));
        l.setConvertedDealId(d.getId());
        activity.log(from, "Converted to deal", d.getName());
        activity.log(to, "Created from lead", l.getName());
        outbox.publish(l.getCompanyId(), EVENT_CONVERTED, "LEAD", l.getId(), Payload.of("leadId", l.getId(),
                "title", l.getName(), "name", l.getName(), "dealId", d.getId(), "dealName", d.getName(),
                "customerId", client.id(), "ownerUserId", l.getOwnerUserId()));
        return Map.of("dealId", d.getId().toString(), "customerId", client.id());
    }

    /** For writes: with CRM_MANAGE_OWN only, another user's lead answers 404 like one of another company. */
    Lead requireManaged(Long id) {
        Lead l = require(id);
        if (CrmScope.ownManage()) {
            OwnScope.assertMine(CrmScope.mine(l), ECrmError.CRM_006);
        }
        return l;
    }

    Lead require(Long id) {
        Lead l = leads.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ECrmError.CRM_006));
        TenantGuard.assertSameCompany(l.getCompanyId(), ECrmError.CRM_006);
        return l;
    }

    private List<LeadRow> rows(List<Lead> list) {
        List<Long> ids = list.stream().map(Lead::getId).toList();
        Map<Long, String> stageNames = setup.stageNames(list.stream().map(Lead::getStageId).distinct().toList());
        Map<Long, long[]> progress = activity.taskProgress(ESubject.LEAD, ids);
        Map<Long, List<CrmLink>> linked = activity.linksOf(ESubject.LEAD, ids);
        LocalDate today = LocalDate.now();
        return list.stream().map(l -> {
            long[] p = progress.getOrDefault(l.getId(), new long[]{0, 0});
            List<LinkDto> labels = linked.getOrDefault(l.getId(), List.of()).stream()
                    .filter(k -> k.getLinkType() == ELinkType.LABEL)
                    .map(k -> new LinkDto(k.getRefId().toString(), k.getRefName())).toList();
            return new LeadRow(l.getId().toString(), l.getPipelineId().toString(), l.getStageId().toString(),
                    stageNames.get(l.getStageId()), l.getName(), l.getEmail(), l.getSubject(), l.getPhone(),
                    l.getFollowUpDate(), l.getFollowUpDate() != null && l.getFollowUpDate().isBefore(today),
                    l.getOwnerUserId().toString(), p[0], p[1], labels, l.getPosition(),
                    l.getConvertedDealId() == null ? null : l.getConvertedDealId().toString(), l.getCreatedDate());
        }).toList();
    }

    /** Notification LEAD_ASSIGNED to the new owner (an IAM user; notification-service resolves the address). */
    private void assigned(Lead l) {
        outbox.publish(l.getCompanyId(), EVENT_ASSIGNED, "LEAD", l.getId(), Payload.of("leadId", l.getId(),
                "pipelineId", l.getPipelineId(), "name", l.getName(), "subject", l.getSubject(),
                "ownerUserId", l.getOwnerUserId(),
                "recipientUserIds", Payload.ids(java.util.Collections.singletonList(l.getOwnerUserId()))));
    }

    private static void apply(Lead l, LeadForm f) {
        l.setName(f.name().trim());
        l.setEmail(f.email().trim());
        l.setSubject(f.subject().trim());
        l.setOwnerUserId(f.ownerUserId());
        l.setPhone(blankOr(f.phone(), null));
        l.setFollowUpDate(f.followUpDate());
        l.setNotes(blankOr(f.notes(), null));
    }

    static Subject subject(Lead l) {
        return new Subject(ESubject.LEAD, l.getId(), l.getCompanyId(), l.getPipelineId());
    }

    private static String like(String keyword) {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
    }

    private static String blankOr(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.trim();
    }
}
