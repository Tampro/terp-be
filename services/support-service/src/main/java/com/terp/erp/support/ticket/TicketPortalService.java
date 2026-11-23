package com.terp.erp.support.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.OwnScope;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.ticket.Attachments.Attachment;
import com.terp.erp.support.ticket.Ticket.EAccountType;
import com.terp.erp.support.ticket.Ticket.EStatus;
import com.terp.erp.support.ticket.TicketFieldService.CustomFieldInput;
import com.terp.erp.support.ticket.TicketFieldService.CustomFieldValue;
import com.terp.erp.support.ticket.TicketReply.ESender;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Client portal tickets: those I created or raised with my account's email (see {@link TicketScope}). Name and email
 * of a new ticket come from my token, never from the request; account type CLIENT. Hidden from the client: the
 * internal note, the tracking token, account type, attachment file ids and staff usernames (a staff reply shows no
 * sender name, as on the public portal).
 */
@Service
@RequiredArgsConstructor
public class TicketPortalService {
    private final TicketRepository tickets;
    private final TicketService service;
    private final TicketFieldService fields;
    private final TicketCategoryService categories;

    public record PortalTicketForm(
            @Schema(description = "Ticket category of my company", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long categoryId,
            @Schema(example = "Cannot download my invoice", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String subject,
            @Schema(description = "HTML; sanitized", example = "<p>The download button does nothing.</p>",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 20_000) String description,
            @Schema(description = "Values of the active custom ticket fields") @Size(max = 100)
            List<@Valid CustomFieldInput> customFields) {
    }

    public record PortalReplyForm(
            @Schema(description = "HTML; sanitized", example = "<p>It works now, thanks.</p>",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 20_000) String description) {
    }

    public record PortalTicketRow(String id, String ticketNumber, String subject, String categoryName, EStatus status,
                                  Instant createdDate, Instant updatedDate) {
    }

    public record PortalReply(ESender senderType, String senderName, String description, List<String> attachmentNames,
                              Instant createdDate) {
    }

    public record PortalTicket(PortalTicketRow ticket, String description, List<String> attachmentNames,
                               List<PortalReply> replies, List<CustomFieldValue> customFields) {
    }

    @Transactional(readOnly = true)
    public Page<PortalTicketRow> list(EStatus status, Pageable pageable) {
        Page<Ticket> page = tickets.searchOwn(companyId(), status, null, null, null, OwnScope.sub(),
                TicketScope.myEmail(), pageable);
        return page.map(this::row);
    }

    @Transactional(readOnly = true)
    public List<TicketCategoryService.CategoryDto> categories() {
        return categories.listOf(companyId());
    }

    @Transactional(readOnly = true)
    public PortalTicket get(Long id) {
        return view(requireMine(id));
    }

    /** IN_PROGRESS, account type CLIENT, requester = me (name and email of my token). */
    @Transactional
    public PortalTicket create(PortalTicketForm f) {
        String email = TicketScope.myEmail();
        if (email == null) {
            throw ApiException.badRequest(ESupportError.SUP_043);
        }
        Long company = companyId();
        Map<Long, String> custom = fields.validate(company, f.customFields());
        // TODO(assumption): fromPortal = true marks a ticket raised by the customer (public or client portal).
        TicketService.Created c = service.open(company, EAccountType.CLIENT, TicketScope.myName(), email,
                f.categoryId(), f.subject(), f.description(), EStatus.IN_PROGRESS, List.<Attachment>of(), true);
        fields.store(company, c.ticket().getId(), custom);
        return view(c.ticket());
    }

    /** Refused on a CLOSED ticket (SUP_006). */
    @Transactional
    public PortalTicket reply(Long id, PortalReplyForm f) {
        Ticket t = requireMine(id);
        service.addReply(t, ESender.CUSTOMER, t.getName(), f.description(), List.of());
        return view(t);
    }

    private Ticket requireMine(Long id) {
        Long company = companyId();
        return tickets.findByIdAndDeletedFalse(id)
                .filter(t -> Objects.equals(t.getCompanyId(), company) && TicketScope.mine(t))
                .orElseThrow(() -> ApiException.notFound(ESupportError.SUP_004));
    }

    /** A client always belongs to a company; a super admin has none, so nothing is found (404). */
    private static Long companyId() {
        Long company = CurrentUser.get().companyId();
        if (company == null) {
            throw ApiException.notFound(ESupportError.SUP_004);
        }
        return company;
    }

    private PortalTicketRow row(Ticket t) {
        return new PortalTicketRow(String.valueOf(t.getId()), t.getTicketNumber(), t.getSubject(),
                service.categoryName(t), t.getStatus(), t.getCreatedDate(), t.getUpdatedDate());
    }

    private PortalTicket view(Ticket t) {
        List<PortalReply> replies = service.conversation(t).stream()
                .map(r -> new PortalReply(r.senderType(), r.senderType() == ESender.STAFF ? null : r.senderName(),
                        r.description(), names(r.attachments()), r.createdDate()))
                .toList();
        return new PortalTicket(row(t), t.getDescription(), names(service.attachmentsOf(t)), replies,
                service.customFieldsOf(t));
    }

    private static List<String> names(List<Attachment> files) {
        return files == null ? List.of() : files.stream().map(Attachment::name).toList();
    }
}
