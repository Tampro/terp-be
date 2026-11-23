package com.terp.erp.iam.helpdesk;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.CategoryDto;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.CategoryForm;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.HelpdeskStats;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.MonthStat;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.ReplyDto;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.ReplyForm;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.TicketCreateForm;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.TicketDetail;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.TicketRow;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.TicketUpdateForm;
import com.terp.erp.iam.me.AvatarMediaClient;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Platform helpdesk (ERP manual 3.3, 11.2): a company opens tickets to the platform and replies to them; the super
 * admin works every company's tickets. Status, priority and category are the super admin's; a reply changes none
 * of them.
 */
@Service
@RequiredArgsConstructor
public class HelpdeskService {
    // TODO(assumption): attachment types and size are not given by the source; images, PDF, text and Office files up
    // to 10 MB, 5 per reply — confirm with PO.
    static final Set<String> ATTACHMENT_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf", "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    static final long ATTACHMENT_MAX_BYTES = 10L * 1024 * 1024;
    static final String EVENT_TICKET_CREATED = "iam.helpdesk.ticket_created";
    static final String EVENT_TICKET_REPLIED = "iam.helpdesk.ticket_replied";

    private final HelpdeskTicketRepository tickets;
    private final HelpdeskReplyRepository replies;
    private final HelpdeskCategoryRepository categories;
    private final CompanyRepository companies;
    private final UserAccountRepository users;
    private final AvatarMediaClient media;
    private final OutboxPublisher outbox;

    // ---------- tickets ----------

    /** A company user sees their own company's tickets; the super admin every company's, or one with companyId. */
    @Transactional(readOnly = true)
    public Page<TicketRow> search(Long companyId, EHelpdeskStatus status, EHelpdeskPriority priority, Long categoryId,
                                  String keyword, Pageable pageable) {
        String kw = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
        Page<HelpdeskTicket> page = tickets.search(scope(companyId), status, priority, categoryId, kw, pageable);
        return page.map(rowMapper(page.getContent()));
    }

    /** Counts for the status tabs, in the same scope as the list. */
    @Transactional(readOnly = true)
    public Map<EHelpdeskStatus, Long> statusCounts(Long companyId) {
        Map<EHelpdeskStatus, Long> counts = new EnumMap<>(EHelpdeskStatus.class);
        for (EHelpdeskStatus s : EHelpdeskStatus.values()) {
            counts.put(s, 0L);
        }
        for (Object[] row : tickets.countByStatus(scope(companyId))) {
            counts.put((EHelpdeskStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public TicketDetail get(Long id) {
        HelpdeskTicket t = require(id);
        boolean platform = CurrentUser.get().superAdmin();
        List<HelpdeskReply> thread = platform
                ? replies.findByTicketIdAndDeletedFalseOrderByIdAsc(t.getId())
                : replies.findByTicketIdAndInternalFalseAndDeletedFalseOrderByIdAsc(t.getId());
        return new TicketDetail(rowMapper(List.of(t)).apply(t), t.getDescription(),
                thread.stream().map(ReplyDto::of).toList());
    }

    @Transactional
    public TicketDetail create(TicketCreateForm f) {
        ErpPrincipal p = CurrentUser.get();
        Long companyId = TenantGuard.resolveCompany(f.companyId(), EHelpdeskError.HDK_008);
        if (companies.findByIdAndDeletedFalse(companyId).isEmpty()) {
            throw ApiException.badRequest(EHelpdeskError.HDK_008);
        }
        if (!p.superAdmin() && f.priority() != null) {
            throw ApiException.forbidden(EHelpdeskError.HDK_009);
        }
        HelpdeskTicket t = new HelpdeskTicket();
        t.setCompanyId(companyId);
        t.setTitle(f.title().trim());
        t.setDescription(f.description());
        t.setCategoryId(activeCategory(f.categoryId(), null));
        t.setStatus(EHelpdeskStatus.OPEN);
        t.setPriority(f.priority() == null ? EHelpdeskPriority.MEDIUM : f.priority());
        tickets.save(t);
        t.setTicketNumber(number(t.getId()));
        // The company user who opened it is told it was received. TODO(assumption): nobody on the platform side is
        // emailed (no platform address list yet); a ticket the super admin opens for a company emails nobody.
        publish(EVENT_TICKET_CREATED, t, p.superAdmin() || p.userId() == null ? List.of() : List.of(p.userId()));
        // The platform team: helpdeskNotifyEmails (when the platform switch is on) and NEW_HELPDESK_TICKET webhooks.
        // TODO(assumption): also when the super admin opened the ticket for a company.
        publishPlatform(com.terp.erp.iam.platform.PlatformEvents.HELPDESK_TICKET_CREATED, t);
        return new TicketDetail(rowMapper(List.of(t)).apply(t), t.getDescription(), List.of());
    }

    /** Super admin: full replacement of title, description, category, status and priority. */
    @Transactional
    public TicketDetail update(Long id, TicketUpdateForm f) {
        HelpdeskTicket t = require(id);
        t.setTitle(f.title().trim());
        t.setDescription(f.description());
        t.setCategoryId(activeCategory(f.categoryId(), t.getCategoryId()));
        t.setPriority(f.priority());
        if (f.status() == EHelpdeskStatus.RESOLVED && t.getStatus() != EHelpdeskStatus.RESOLVED) {
            t.setResolvedAt(Instant.now());
        } else if (f.status() == EHelpdeskStatus.OPEN || f.status() == EHelpdeskStatus.IN_PROGRESS) {
            t.setResolvedAt(null);
        }
        t.setStatus(f.status());
        return get(t.getId());
    }

    /** Super admin: soft delete; the conversation goes with it. */
    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    // ---------- replies ----------

    @Transactional
    public ReplyDto reply(Long ticketId, ReplyForm f) {
        ErpPrincipal p = CurrentUser.get();
        HelpdeskTicket t = require(ticketId);
        boolean internal = Boolean.TRUE.equals(f.internal());
        if (internal && !p.superAdmin()) {
            throw ApiException.forbidden(EHelpdeskError.HDK_005);
        }
        // TODO(assumption): a closed ticket takes no replies from anyone (the support portal rule of the manual,
        // 11.1); the super admin reopens it first — confirm with PO.
        if (t.getStatus() == EHelpdeskStatus.CLOSED) {
            throw ApiException.badRequest(EHelpdeskError.HDK_004);
        }
        List<Long> files = f.attachmentFileIds() == null ? List.of() : List.copyOf(f.attachmentFileIds());
        for (Long fileId : files) {
            media.requireCompanyFile(fileId, t.getCompanyId(), ATTACHMENT_TYPES, ATTACHMENT_MAX_BYTES,
                    EHelpdeskError.HDK_007);
        }
        HelpdeskReply r = new HelpdeskReply();
        r.setTicketId(t.getId());
        r.setCompanyId(t.getCompanyId());
        r.setMessage(f.message());
        r.setInternal(internal);
        r.setFromPlatform(p.superAdmin());
        r.setAuthorName(authorName(p));
        r.setAttachmentFileIds(new ArrayList<>(files));
        ReplyDto dto = ReplyDto.of(replies.save(r));
        // An internal note is never announced. A platform reply goes to the ticket's creator; a company reply to
        // nobody by email (TODO(assumption): no platform recipient yet), but still to the company's chat channels.
        if (!internal) {
            List<Long> to = p.superAdmin() ? creator(t) : List.of();
            publish(EVENT_TICKET_REPLIED, t, to);
            // A company's reply is the platform team's business; the platform's own reply is not announced to it.
            if (!p.superAdmin()) {
                publishPlatform(com.terp.erp.iam.platform.PlatformEvents.HELPDESK_TICKET_REPLIED, t);
            }
        }
        return dto;
    }

    /** Super admin: removes one message of the conversation. */
    @Transactional
    public void deleteReply(Long ticketId, Long replyId) {
        HelpdeskTicket t = require(ticketId);
        replies.findByIdAndTicketIdAndDeletedFalse(replyId, t.getId())
                .orElseThrow(() -> ApiException.notFound(EHelpdeskError.HDK_006))
                .setDeleted(true);
    }

    // ---------- categories ----------

    /** Super admin: every category; a company: the active ones it can choose. */
    @Transactional(readOnly = true)
    public List<CategoryDto> categories() {
        List<HelpdeskCategory> all = CurrentUser.get().superAdmin()
                ? categories.findByDeletedFalseOrderByNameAsc()
                : categories.findByActiveTrueAndDeletedFalseOrderByNameAsc();
        return all.stream().map(CategoryDto::of).toList();
    }

    @Transactional
    public CategoryDto createCategory(CategoryForm f) {
        HelpdeskCategory c = new HelpdeskCategory();
        apply(c, f);
        return CategoryDto.of(categories.save(c));
    }

    /** Full replacement. */
    @Transactional
    public CategoryDto updateCategory(Long id, CategoryForm f) {
        HelpdeskCategory c = requireCategory(id);
        apply(c, f);
        return CategoryDto.of(c);
    }

    /** Soft delete: tickets keep pointing at it and still show its name. */
    @Transactional
    public void deleteCategory(Long id) {
        requireCategory(id).setDeleted(true);
    }

    // ---------- dashboard ----------

    @Transactional(readOnly = true)
    public HelpdeskStats stats(Integer year) {
        int y = year == null ? LocalDate.now(ZoneOffset.UTC).getYear() : year;
        Instant from = LocalDate.of(y, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = LocalDate.of(y + 1, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Map<Integer, Long> created = perMonth(tickets.createdPerMonth(from, to));
        Map<Integer, Long> resolved = perMonth(tickets.resolvedPerMonth(from, to));
        List<MonthStat> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            months.add(new MonthStat(m, created.getOrDefault(m, 0L), resolved.getOrDefault(m, 0L)));
        }
        Map<EHelpdeskStatus, Long> byStatus = statusCounts(null);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new HelpdeskStats(y, months, byStatus, total);
    }

    // ---------- helpers ----------

    /** Display fields only: never the description or a reply's text (they may hold anything). */
    private void publish(String eventType, HelpdeskTicket t, List<Long> recipientUserIds) {
        outbox.publish(t.getCompanyId(), eventType, "HELPDESK_TICKET", t.getId(), Payload.of(
                "ticketId", t.getId(), "ticketNumber", t.getTicketNumber(), "title", "#" + t.getTicketNumber(),
                "subject", t.getTitle(), "status", t.getStatus() == null ? null : t.getStatus().name(),
                "recipientUserIds", Payload.ids(recipientUserIds)));
    }

    /** companyId null (platform event); the company travels in the payload. Display fields only, as above. */
    private void publishPlatform(String eventType, HelpdeskTicket t) {
        com.terp.erp.iam.platform.PlatformEvents.publish(outbox, eventType, "HELPDESK_TICKET", t.getId(), Payload.of(
                "ticketId", t.getId(), "ticketNumber", t.getTicketNumber(), "title", "#" + t.getTicketNumber(),
                "subject", t.getTitle(), "status", t.getStatus() == null ? null : t.getStatus().name(),
                "companyId", t.getCompanyId(), "companyName", companyName(t.getCompanyId())));
    }

    private String companyName(Long companyId) {
        return companyId == null ? null : companies.findById(companyId).map(Company::getName).orElse(null);
    }

    /** The IAM user who opened the ticket (created_by = Keycloak sub); none when unknown or deleted. */
    private List<Long> creator(HelpdeskTicket t) {
        if (t.getCreatedBy() == null) {
            return List.of();
        }
        return users.findByKeycloakIdAndDeletedFalse(t.getCreatedBy()).filter(u -> u.getCompanyId() != null
                && u.getCompanyId().equals(t.getCompanyId())).map(u -> List.of(u.getId())).orElse(List.of());
    }

    private HelpdeskTicket require(Long id) {
        HelpdeskTicket t = tickets.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EHelpdeskError.HDK_001));
        TenantGuard.assertSameCompany(t.getCompanyId(), EHelpdeskError.HDK_001);
        return t;
    }

    private HelpdeskCategory requireCategory(Long id) {
        return categories.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EHelpdeskError.HDK_002));
    }

    /** Super admin: the requested company or all (null); a company user: always their own. */
    private static Long scope(Long requested) {
        ErpPrincipal p = CurrentUser.get();
        return p.superAdmin() ? requested : TenantGuard.resolveCompany(null, EHelpdeskError.HDK_008);
    }

    /** null stays null; the category already on the ticket may stay even if deactivated since. */
    private Long activeCategory(Long categoryId, Long current) {
        if (categoryId == null) {
            return null;
        }
        HelpdeskCategory c = requireCategory(categoryId);
        if (!c.isActive() && !Objects.equals(categoryId, current)) {
            throw ApiException.badRequest(EHelpdeskError.HDK_003);
        }
        return c.getId();
    }

    private static void apply(HelpdeskCategory c, CategoryForm f) {
        c.setName(f.name().trim());
        c.setDescription(f.description() == null || f.description().isBlank() ? null : f.description().trim());
        c.setColor(f.color().toUpperCase());
        c.setActive(f.active());
    }

    private String authorName(ErpPrincipal p) {
        if (p.userId() == null) {
            return p.username();
        }
        return users.findByIdAndDeletedFalse(p.userId()).map(u -> u.getFullName()).orElse(p.username());
    }

    private Function<HelpdeskTicket, TicketRow> rowMapper(Collection<HelpdeskTicket> rows) {
        Set<Long> companyIds = rows.stream().map(HelpdeskTicket::getCompanyId).collect(Collectors.toSet());
        Set<Long> categoryIds = rows.stream().map(HelpdeskTicket::getCategoryId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> companyNames = new HashMap<>();
        companies.findAllById(companyIds).forEach((Company c) -> companyNames.put(c.getId(), c.getName()));
        Map<Long, HelpdeskCategory> cats = categoryIds.isEmpty() ? Map.of()
                : categories.findByIdIn(categoryIds).stream()
                .collect(Collectors.toMap(HelpdeskCategory::getId, Function.identity()));
        return t -> {
            HelpdeskCategory c = t.getCategoryId() == null ? null : cats.get(t.getCategoryId());
            return new TicketRow(t.getId().toString(), "#" + t.getTicketNumber(), t.getTitle(),
                    t.getCompanyId().toString(), companyNames.get(t.getCompanyId()),
                    c == null ? null : c.getId().toString(), c == null ? null : c.getName(),
                    c == null ? null : c.getColor(), t.getStatus(), t.getPriority(), t.getCreatedDate(),
                    t.getUpdatedDate(), t.getResolvedAt());
        };
    }

    private static Map<Integer, Long> perMonth(List<Object[]> rows) {
        Map<Integer, Long> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put(((Number) r[0]).intValue(), ((Number) r[1]).longValue());
        }
        return m;
    }

    static String number(Long id) {
        return String.format("%08d", id);
    }
}
