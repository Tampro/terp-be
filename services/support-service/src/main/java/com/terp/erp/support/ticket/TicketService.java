package com.terp.erp.support.ticket;

import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.TenantGuard;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.common.HtmlSanitizer;
import com.terp.erp.support.common.Texts;
import com.terp.erp.support.ticket.Attachments.Attachment;
import com.terp.erp.support.ticket.Ticket.EAccountType;
import com.terp.erp.support.ticket.Ticket.EStatus;
import com.terp.erp.support.ticket.TicketFieldService.CustomFieldInput;
import com.terp.erp.support.ticket.TicketFieldService.CustomFieldValue;
import com.terp.erp.support.ticket.TicketReply.ESender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Support tickets (ERP manual 18; reference SupportTicketController / ConversationController): numbering per company,
 * tabs by status, staff conversation. A CLOSED ticket takes no reply, from staff or from the customer.
 */
@Service
@RequiredArgsConstructor
public class TicketService {
    static final String EVENT_CREATED = "support.ticket.created";
    static final String EVENT_REPLIED = "support.ticket.replied";

    public record TicketRow(String id, String ticketNumber, EAccountType accountType, String name, String email,
                            String categoryId, String categoryName, String subject, EStatus status,
                            boolean fromPortal, Instant createdDate, Instant updatedDate) {
    }

    public record ReplyDto(String id, ESender senderType, String senderName, String description,
                           List<Attachment> attachments, Instant createdDate) {
    }

    /** note is internal: only this admin view carries it. */
    public record TicketDto(TicketRow ticket, String description, List<Attachment> attachments, String note,
                            List<ReplyDto> replies, List<CustomFieldValue> customFields) {
    }

    /** trackingToken: shown once; give it to the customer to open the ticket on the portal. */
    public record CreatedTicketDto(TicketDto ticket, String trackingToken) {
    }

    public record StatusCounts(long all, long inProgress, long onHold, long closed) {
    }

    public record TicketForm(
            @Schema(example = "CUSTOM", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EAccountType accountType,
            @Schema(example = "Test Customer", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255)
            String name,
            @Schema(example = "customer@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 255) String email,
            @Schema(description = "Ticket category id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long categoryId,
            @Schema(example = "Cannot download my invoice", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String subject,
            @Schema(description = "HTML; sanitized", example = "<p>The download button does nothing.</p>",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100_000) String description,
            @Schema(description = "Create: default IN_PROGRESS. Update: null keeps the current status")
            EStatus status,
            @Schema(description = "media-service file ids of the company, at most 10") @Size(max = 10)
            List<Long> attachmentFileIds,
            @Schema(description = "Internal note, never shown on the portal") @Size(max = 5000) String note,
            @Schema(description = "Values of the company's active ticket fields (GET /support/ticket-fields). "
                    + "Update: replaces the values of active fields; values of inactive fields stay")
            @Size(max = 100) List<@Valid CustomFieldInput> customFields) {
    }

    public record StatusForm(
            @Schema(example = "ON_HOLD", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }

    public record ReplyForm(
            @Schema(description = "HTML; sanitized", example = "<p>Please try again now.</p>",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100_000) String description,
            @Schema(description = "media-service file ids of the company, at most 10") @Size(max = 10)
            List<Long> attachmentFileIds) {
    }

    /** A new ticket and its raw tracking token (only the hash is stored). */
    public record Created(Ticket ticket, String trackingToken) {
    }

    private final TicketRepository tickets;
    private final TicketReplyRepository replies;
    private final TicketCategoryRepository categoryRepo;
    private final TicketCategoryService categories;
    private final TicketNumbers numbers;
    private final Attachments attachments;
    private final TicketFieldService fields;
    private final OutboxPublisher outbox;

    @Transactional(readOnly = true)
    public Page<TicketRow> list(Long requestedCompanyId, EStatus status, Long categoryId, EAccountType accountType,
                                String keyword, Pageable pageable) {
        Long companyId = Texts.company(requestedCompanyId);
        Page<Ticket> page = tickets.search(companyId, status, categoryId, accountType, Texts.kw(keyword), pageable);
        Map<Long, String> names = categoryNames(page.getContent());
        return page.map(t -> row(t, names));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId) {
        Map<EStatus, Long> by = new HashMap<>();
        for (Object[] r : tickets.countByStatus(Texts.company(requestedCompanyId))) {
            by.put((EStatus) r[0], ((Number) r[1]).longValue());
        }
        long in = by.getOrDefault(EStatus.IN_PROGRESS, 0L);
        long hold = by.getOrDefault(EStatus.ON_HOLD, 0L);
        long closed = by.getOrDefault(EStatus.CLOSED, 0L);
        return new StatusCounts(in + hold + closed, in, hold, closed);
    }

    @Transactional(readOnly = true)
    public TicketDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public CreatedTicketDto create(Long requestedCompanyId, TicketForm f) {
        Long companyId = Texts.company(requestedCompanyId);
        List<Attachment> files = attachments.resolve(f.attachmentFileIds(), companyId);
        Map<Long, String> custom = fields.validate(companyId, f.customFields());
        Created c = open(companyId, f.accountType(), f.name(), f.email(), f.categoryId(), f.subject(),
                f.description(), f.status(), files, false);
        c.ticket().setNote(Texts.blankToNull(f.note()));
        fields.store(companyId, c.ticket().getId(), custom);
        return new CreatedTicketDto(dto(c.ticket()), c.trackingToken());
    }

    /** Full replacement of the ticket's fields; status null keeps the current one; the tracking token stays. */
    @Transactional
    public TicketDto update(Long id, TicketForm f) {
        Ticket t = require(id);
        categories.requireForTicket(f.categoryId(), t.getCompanyId());
        Map<Long, String> custom = fields.validate(t.getCompanyId(), f.customFields());
        t.setAccountType(f.accountType());
        t.setName(f.name().trim());
        t.setEmail(Texts.email(f.email()));
        t.setCategoryId(f.categoryId());
        t.setSubject(f.subject().trim());
        t.setDescription(cleanRequired(f.description()));
        if (f.status() != null) {
            t.setStatus(f.status());
        }
        t.setAttachments(attachments.write(attachments.resolve(f.attachmentFileIds(), t.getCompanyId())));
        t.setNote(Texts.blankToNull(f.note()));
        fields.store(t.getCompanyId(), t.getId(), custom);
        return dto(t);
    }

    @Transactional
    public TicketRow changeStatus(Long id, StatusForm f) {
        Ticket t = require(id);
        t.setStatus(f.status());
        return row(t, categoryNames(List.of(t)));
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    /** Staff reply; the sender name is the caller's username. */
    @Transactional
    public ReplyDto reply(Long id, ReplyForm f) {
        Ticket t = require(id);
        List<Attachment> files = attachments.resolve(f.attachmentFileIds(), t.getCompanyId());
        return replyDto(addReply(t, ESender.STAFF, CurrentUser.get().username(), f.description(), files));
    }

    // ---- shared with the public portal ----

    /** Numbers and saves a new ticket; the category must belong to the company (SUP_005). */
    public Created open(Long companyId, EAccountType accountType, String name, String email, Long categoryId, String subject,
                 String description, EStatus status, List<Attachment> files, boolean fromPortal) {
        categories.requireForTicket(categoryId, companyId);
        String token = TrackingTokens.newToken();
        Ticket t = new Ticket();
        t.setCompanyId(companyId);
        t.setTicketNumber(numbers.next(companyId));
        t.setAccountType(accountType);
        t.setName(name.trim());
        t.setEmail(Texts.email(email));
        t.setCategoryId(categoryId);
        t.setSubject(subject.trim());
        t.setDescription(cleanRequired(description));
        t.setStatus(status == null ? EStatus.IN_PROGRESS : status);
        t.setAttachments(attachments.write(files));
        t.setTrackingTokenHash(TrackingTokens.hash(token));
        t.setFromPortal(fromPortal);
        Ticket saved = tickets.save(t);
        Ticket ref = saved == null ? t : saved;
        // Notification SUPPORT_TICKET_CREATED to the requester's email (the tracking token never leaves this service).
        outbox.publish(companyId, EVENT_CREATED, "TICKET", String.valueOf(ref.getId()), Payload.of(
                "ticketId", ref.getId(), "ticketNumber", t.getTicketNumber(), "subject", t.getSubject(),
                "name", t.getName(), "categoryId", categoryId, "fromPortal", fromPortal,
                "recipientEmail", t.getEmail()));
        return new Created(saved, token);
    }

    /** Refuses a CLOSED ticket (SUP_006) whoever replies. */
    public TicketReply addReply(Ticket t, ESender sender, String senderName, String html, List<Attachment> files) {
        if (t.getStatus() == EStatus.CLOSED) {
            throw ApiException.badRequest(ESupportError.SUP_006);
        }
        TicketReply r = new TicketReply();
        r.setTicketId(t.getId());
        r.setSenderType(sender);
        r.setSenderName(senderName);
        r.setDescription(cleanRequired(html));
        r.setAttachments(attachments.write(files));
        TicketReply saved = replies.save(r);
        // The list orders by last activity: touch the ticket.
        t.setUpdatedDate(Instant.now());
        // Notification SUPPORT_TICKET_REPLY: a staff reply goes to the requester; a customer's reply has no
        // recipient yet. TODO(assumption): notify the assigned staff once tickets have an assignee.
        outbox.publish(t.getCompanyId(), EVENT_REPLIED, "TICKET", String.valueOf(t.getId()), Payload.of(
                "ticketId", t.getId(), "ticketNumber", t.getTicketNumber(), "subject", t.getSubject(),
                "senderType", sender, "recipientEmail", sender == ESender.STAFF ? t.getEmail() : null));
        return saved;
    }

    public List<ReplyDto> conversation(Ticket t) {
        return replies.findByTicketIdOrderByIdAsc(t.getId()).stream().map(this::replyDto).toList();
    }

    public List<Attachment> attachmentsOf(Ticket t) {
        return attachments.read(t.getAttachments());
    }

    public List<CustomFieldValue> customFieldsOf(Ticket t) {
        return fields.valuesOf(t.getId());
    }

    public String categoryName(Ticket t) {
        return t.getCategoryId() == null ? null : categoryNames(List.of(t)).get(t.getCategoryId());
    }

    // ----

    private Ticket require(Long id) {
        Ticket t = tickets.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(ESupportError.SUP_004));
        TenantGuard.assertSameCompany(t.getCompanyId(), ESupportError.SUP_004);
        return t;
    }

    private static String cleanRequired(String html) {
        if (HtmlSanitizer.isBlank(html)) {
            throw ApiException.badRequest(ESupportError.SUP_034);
        }
        return HtmlSanitizer.clean(html);
    }

    private Map<Long, String> categoryNames(List<Ticket> list) {
        List<Long> ids = list.stream().map(Ticket::getCategoryId).filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(TicketCategory::getId, TicketCategory::getName));
    }

    private TicketDto dto(Ticket t) {
        return new TicketDto(row(t, categoryNames(List.of(t))), t.getDescription(), attachmentsOf(t), t.getNote(),
                conversation(t), customFieldsOf(t));
    }

    private static TicketRow row(Ticket t, Map<Long, String> names) {
        return new TicketRow(String.valueOf(t.getId()), t.getTicketNumber(), t.getAccountType(), t.getName(),
                t.getEmail(), t.getCategoryId() == null ? null : t.getCategoryId().toString(),
                t.getCategoryId() == null ? null : names.get(t.getCategoryId()), t.getSubject(), t.getStatus(),
                t.isFromPortal(), t.getCreatedDate(),
                t.getUpdatedDate());
    }

    private ReplyDto replyDto(TicketReply r) {
        return new ReplyDto(String.valueOf(r.getId()), r.getSenderType(), r.getSenderName(), r.getDescription(),
                attachments.read(r.getAttachments()), r.getCreatedDate());
    }
}
