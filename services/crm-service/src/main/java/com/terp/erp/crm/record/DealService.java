package com.terp.erp.crm.record;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.crm.common.ECrmError;
import com.terp.erp.crm.record.ActivityService.Activity;
import com.terp.erp.crm.record.ActivityService.LinkDto;
import com.terp.erp.crm.record.ActivityService.Subject;
import com.terp.erp.crm.record.CrmEnums.EDealStatus;
import com.terp.erp.crm.record.CrmEnums.ELinkType;
import com.terp.erp.crm.record.CrmEnums.ESubject;
import com.terp.erp.crm.record.LeadService.LinksForm;
import com.terp.erp.crm.record.LeadService.MoveForm;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Deals (ERP manual 7.3): list, kanban by deal stage, detail, status ACTIVE / WON / LOSS, clients. */
@Service
@RequiredArgsConstructor
public class DealService {
    static final String EVENT_ASSIGNED = "crm.deal.assigned";
    static final String EVENT_MOVED = "crm.deal.moved";
    static final String EVENT_CREATED = "crm.deal.created";
    private final DealRepository deals;
    private final SetupService setup;
    private final ActivityService activity;
    private final OutboxPublisher outbox;

    public record DealForm(
            @Schema(description = "Pipeline id (it decides the company)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long pipelineId,
            @Schema(description = "Deal stage of the pipeline; omit = its first deal stage", example = "4") Long stageId,
            @Schema(description = "Deal name", example = "20-seat plan", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String name,
            @Schema(description = "Price", example = "12000000", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin("0") BigDecimal price,
            @Schema(description = "Phone", example = "+84 90 000 0000") @Size(max = 30) String phone,
            @Schema(description = "Clients: accounting customer ids (replaces the current ones)", example = "[\"5\"]")
            List<@NotNull Long> clientIds,
            @Schema(description = "Notes (rich text)", example = "<p>Budget approved</p>") @Size(max = 20000) String notes) {
    }

    public record StatusForm(
            @Schema(description = "ACTIVE, WON or LOSS", example = "WON", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EDealStatus status) {
    }

    public record DealRow(String id, String pipelineId, String stageId, String stageName, String name, BigDecimal price,
                          String phone, EDealStatus status, long tasksTotal, long tasksDone, List<LinkDto> clients,
                          List<LinkDto> labels, int position, String fromLeadId, Instant createdDate) {
    }

    public record DealDetail(DealRow deal, String notes, Activity activity) {
    }

    public record Column(String stageId, String name, long count, BigDecimal total, List<DealRow> deals) {
    }

    @Transactional(readOnly = true)
    public Page<DealRow> search(Long companyId, Long pipelineId, Long stageId, EDealStatus status, String keyword,
                                Pageable pageable) {
        Long company = TenantGuard.resolveCompany(companyId, ECrmError.CRM_001);
        Page<Deal> page = CrmScope.ownView()
                ? deals.searchOwn(company, pipelineId, stageId, status, like(keyword), OwnScope.userId(),
                OwnScope.sub(), ESubject.DEAL, ELinkType.MEMBER, pageable)
                : deals.search(company, pipelineId, stageId, status, like(keyword), pageable);
        List<DealRow> rows = rows(page.getContent());
        return page.map(d -> rows.get(page.getContent().indexOf(d)));
    }

    /** The deal board of a pipeline: one column per deal stage with its count and total price. */
    @Transactional(readOnly = true)
    public List<Column> board(Long pipelineId) {
        Pipeline p = setup.requirePipeline(pipelineId);
        boolean own = CrmScope.ownView();
        List<DealRow> rows = rows(deals.findByCompanyIdAndPipelineIdAndDeletedFalseOrderByPositionAscIdAsc(
                p.getCompanyId(), p.getId()).stream().filter(d -> !own || activity.isMine(d)).toList());
        List<Column> out = new ArrayList<>();
        for (Stage s : setup.stagesOf(p.getId(), EStageKind.DEAL)) {
            List<DealRow> in = rows.stream().filter(r -> r.stageId().equals(s.getId().toString())).toList();
            out.add(new Column(s.getId().toString(), s.getName(), in.size(),
                    in.stream().map(DealRow::price).reduce(BigDecimal.ZERO, BigDecimal::add), in));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public DealDetail get(Long id) {
        Deal d = require(id);
        if (CrmScope.ownView()) {
            OwnScope.assertMine(activity.isMine(d), ECrmError.CRM_007);
        }
        return detail(d);
    }

    private DealDetail detail(Deal d) {
        return new DealDetail(rows(List.of(d)).get(0), d.getNotes(), activity.activity(subject(d)));
    }

    @Transactional
    public DealDetail create(DealForm f) {
        Pipeline p = setup.requirePipeline(f.pipelineId());
        Stage s = f.stageId() == null ? setup.firstStage(p.getId(), EStageKind.DEAL)
                : setup.requireStageOf(f.stageId(), p.getId(), EStageKind.DEAL);
        Deal d = new Deal();
        d.setCompanyId(p.getCompanyId());
        d.setPipelineId(p.getId());
        d.setStageId(s.getId());
        d.setPosition(deals.maxPosition(s.getId()) + 1);
        apply(d, f);
        deals.save(d);
        activity.log(subject(d), "Deal created", d.getName());
        outbox.publish(d.getCompanyId(), EVENT_CREATED, "DEAL", d.getId(), Payload.of("dealId", d.getId(),
                "title", d.getName(), "pipelineId", d.getPipelineId(), "name", d.getName(), "price", d.getPrice(),
                "stage", s.getName()));
        if (f.clientIds() != null) {
            activity.setLinks(subject(d), ELinkType.CLIENT, f.clientIds());
        }
        return detail(d);
    }

    /** Full replacement of name, price, phone, notes and (when given) clients; stage changes through move. */
    @Transactional
    public DealDetail update(Long id, DealForm f) {
        Deal d = requireManaged(id);
        apply(d, f);
        if (f.clientIds() != null) {
            activity.setLinks(subject(d), ELinkType.CLIENT, f.clientIds());
        }
        return detail(d);
    }

    @Transactional
    public void delete(Long id) {
        requireManaged(id).setDeleted(true);
    }

    @Transactional
    public DealDetail status(Long id, StatusForm f) {
        Deal d = requireManaged(id);
        if (d.getStatus() != f.status()) {
            activity.log(subject(d), "Status", d.getStatus() + " → " + f.status());
            d.setStatus(f.status());
        }
        return detail(d);
    }

    @Transactional
    public DealDetail move(Long id, MoveForm f) {
        Deal d = requireManaged(id);
        Stage to = setup.requireStageOf(f.stageId(), d.getPipelineId(), EStageKind.DEAL);
        String fromName = setup.stageNames(List.of(d.getStageId())).get(d.getStageId());
        List<Deal> column = deals.findByCompanyIdAndPipelineIdAndDeletedFalseOrderByPositionAscIdAsc(
                d.getCompanyId(), d.getPipelineId()).stream()
                .filter(x -> x.getStageId().equals(to.getId()) && !x.getId().equals(d.getId())).toList();
        int pos = f.position() == null ? column.size() + 1 : Math.max(1, Math.min(f.position(), column.size() + 1));
        int i = 1;
        for (Deal x : column) {
            if (i == pos) {
                i++;
            }
            x.setPosition(i++);
        }
        d.setStageId(to.getId());
        d.setPosition(pos);
        if (!to.getName().equals(fromName)) {
            activity.log(subject(d), "Moved", fromName + " → " + to.getName());
            // Notifications DEAL_MOVED (email to the deal's members, webhook DEAL_MOVED).
            outbox.publish(d.getCompanyId(), EVENT_MOVED, "DEAL", d.getId(), Payload.of("dealId", d.getId(),
                    "pipelineId", d.getPipelineId(), "name", d.getName(), "price", d.getPrice(),
                    "fromStage", fromName, "toStage", to.getName(),
                    "recipientUserIds", Payload.ids(members(d.getId()))));
        }
        return detail(d);
    }

    @Transactional
    public List<LinkDto> setLinks(Long id, ELinkType type, LinksForm f) {
        Deal d = requireManaged(id);
        if (type != ELinkType.MEMBER) {
            return activity.setLinks(subject(d), type, f.ids());
        }
        List<Long> before = members(d.getId());
        List<LinkDto> after = activity.setLinks(subject(d), type, f.ids());
        List<Long> added = after.stream().map(l -> Long.valueOf(l.refId())).filter(x -> !before.contains(x)).toList();
        if (!added.isEmpty()) {
            // Notification DEAL_ASSIGNED to the members just added (IAM users).
            outbox.publish(d.getCompanyId(), EVENT_ASSIGNED, "DEAL", d.getId(), Payload.of("dealId", d.getId(),
                    "pipelineId", d.getPipelineId(), "name", d.getName(), "price", d.getPrice(),
                    "recipientUserIds", Payload.ids(added)));
        }
        return after;
    }

    /** User ids linked to the deal as MEMBER. */
    private List<Long> members(Long dealId) {
        return activity.linksOf(ESubject.DEAL, List.of(dealId)).getOrDefault(dealId, List.of()).stream()
                .filter(k -> k.getLinkType() == ELinkType.MEMBER).map(CrmLink::getRefId).toList();
    }

    /** For writes: with CRM_MANAGE_OWN only, a deal I neither created nor am a member of answers 404. */
    Deal requireManaged(Long id) {
        Deal d = require(id);
        if (CrmScope.ownManage()) {
            OwnScope.assertMine(activity.isMine(d), ECrmError.CRM_007);
        }
        return d;
    }

    Deal require(Long id) {
        Deal d = deals.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ECrmError.CRM_007));
        TenantGuard.assertSameCompany(d.getCompanyId(), ECrmError.CRM_007);
        return d;
    }

    private List<DealRow> rows(List<Deal> list) {
        List<Long> ids = list.stream().map(Deal::getId).toList();
        Map<Long, String> stageNames = setup.stageNames(list.stream().map(Deal::getStageId).distinct().toList());
        Map<Long, long[]> progress = activity.taskProgress(ESubject.DEAL, ids);
        Map<Long, List<CrmLink>> linked = activity.linksOf(ESubject.DEAL, ids);
        return list.stream().map(d -> {
            long[] p = progress.getOrDefault(d.getId(), new long[]{0, 0});
            List<CrmLink> ls = linked.getOrDefault(d.getId(), List.of());
            return new DealRow(d.getId().toString(), d.getPipelineId().toString(), d.getStageId().toString(),
                    stageNames.get(d.getStageId()), d.getName(), d.getPrice(), d.getPhone(), d.getStatus(), p[0], p[1],
                    of(ls, ELinkType.CLIENT), of(ls, ELinkType.LABEL), d.getPosition(),
                    d.getFromLeadId() == null ? null : d.getFromLeadId().toString(), d.getCreatedDate());
        }).toList();
    }

    private static List<LinkDto> of(List<CrmLink> ls, ELinkType type) {
        return ls.stream().filter(k -> k.getLinkType() == type)
                .map(k -> new LinkDto(k.getRefId().toString(), k.getRefName())).toList();
    }

    private static void apply(Deal d, DealForm f) {
        d.setName(f.name().trim());
        d.setPrice(f.price());
        d.setPhone(f.phone() == null || f.phone().isBlank() ? null : f.phone().trim());
        d.setNotes(f.notes() == null || f.notes().isBlank() ? null : f.notes().trim());
    }

    static Subject subject(Deal d) {
        return new Subject(ESubject.DEAL, d.getId(), d.getCompanyId(), d.getPipelineId());
    }

    private static String like(String keyword) {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
    }
}
