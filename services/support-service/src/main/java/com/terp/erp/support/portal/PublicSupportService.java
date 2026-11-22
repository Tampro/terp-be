package com.terp.erp.support.portal;

import com.terp.erp.common.ResponseListDto;
import com.terp.erp.exception.ApiException;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.common.Texts;
import com.terp.erp.support.contact.ContactService;
import com.terp.erp.support.kb.FaqService;
import com.terp.erp.support.kb.KnowledgeService;
import com.terp.erp.support.portal.PortalSettingsService.PageDto;
import com.terp.erp.support.portal.PortalSettingsService.PageRow;
import com.terp.erp.support.portal.PortalSettingsService.PortalContent;
import com.terp.erp.support.ticket.Attachments.Attachment;
import com.terp.erp.support.ticket.Ticket;
import com.terp.erp.support.ticket.Ticket.EStatus;
import com.terp.erp.support.ticket.TicketCategoryService;
import com.terp.erp.support.ticket.TicketCategoryService.CategoryDto;
import com.terp.erp.support.ticket.TicketFieldService;
import com.terp.erp.support.ticket.TicketFieldService.CustomFieldInput;
import com.terp.erp.support.ticket.TicketFieldService.CustomFieldValue;
import com.terp.erp.support.ticket.TicketFieldService.FieldDto;
import com.terp.erp.support.ticket.TicketReply.ESender;
import com.terp.erp.support.ticket.TicketRepository;
import com.terp.erp.support.ticket.TicketService;
import com.terp.erp.support.ticket.TrackingTokens;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The public support portal of one company, addressed by its slug (reference FrontendController). No token: the slug
 * picks the company and the portal must be enabled (SUP_009 otherwise). A ticket is reached only with its number +
 * email, or its tracking token; any mismatch answers the same SUP_010, so the portal does not tell which part was
 * wrong. The internal note and staff usernames never leave this class.
 * Public submissions carry no attachment: media-service needs a token to check a file's company, and an anonymous
 * upload endpoint would let anyone store files in a company's library. TODO(assumption): customers add files only
 * after staff ask for them by other means — confirm with PO.
 */
@Service
@RequiredArgsConstructor
public class PublicSupportService {
    public record PortalInfo(String slug, String brandName, String logoUrl, String faviconUrl, PortalContent content,
                             List<PageRow> pages, List<CategoryDto> ticketCategories,
                             List<KnowledgeService.CategoryDto> kbCategories,
                             @Schema(description = "Active custom ticket fields, in display order") List<FieldDto> ticketFields) {
    }

    public record PublicTicketForm(
            @Schema(example = "Test Customer", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 255)
            String name,
            @Schema(example = "customer@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 255) String email,
            @Schema(description = "One of ticketCategories of the portal info", example = "1",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long categoryId,
            @Schema(example = "Cannot download my invoice", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String subject,
            @Schema(description = "HTML; sanitized", example = "<p>The download button does nothing.</p>",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 20_000) String description,
            @Schema(description = "Values of ticketFields of the portal info") @Size(max = 100)
            List<@Valid CustomFieldInput> customFields) {
    }

    /** Keep trackingToken: it opens the ticket without the email (a link the FE can build). */
    public record PublicTicketCreated(String ticketNumber, String trackingToken, EStatus status) {
    }

    public record TrackForm(
            @Schema(example = "TKT-000001") @Size(max = 20) String ticketNumber,
            @Schema(example = "customer@example.com") @Size(max = 255) String email,
            @Schema(description = "Instead of number + email") @Size(max = 64) String trackingToken) {
    }

    public record PublicReplyForm(
            @Schema(example = "TKT-000001") @Size(max = 20) String ticketNumber,
            @Schema(example = "customer@example.com") @Size(max = 255) String email,
            @Schema(description = "Instead of number + email") @Size(max = 64) String trackingToken,
            @Schema(description = "HTML; sanitized", example = "<p>It works now, thanks.</p>",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 20_000) String description) {
    }

    /** senderName is null for staff: the portal shows the company, not the staff account. */
    public record PublicReply(ESender senderType, String senderName, String description, List<String> attachmentNames,
                              Instant createdDate) {
    }

    public record PublicTicketView(String ticketNumber, String name, String subject, String categoryName,
                                   EStatus status, String description, List<String> attachmentNames,
                                   Instant createdDate, Instant updatedDate, List<PublicReply> replies,
                                   List<CustomFieldValue> customFields) {
    }

    private final PortalSettingsRepository settings;
    private final PortalPageRepository pages;
    private final PortalSettingsService settingsService;
    private final TicketCategoryService ticketCategories;
    private final TicketService tickets;
    private final TicketRepository ticketRepo;
    private final KnowledgeService kb;
    private final FaqService faqs;
    private final ContactService contacts;
    private final TicketFieldService ticketFields;

    @Transactional(readOnly = true)
    public PortalInfo info(String slug) {
        PortalSettings s = portal(slug);
        List<PageRow> pageRows = pages.findByCompanyIdAndDeletedFalseOrderByTitleAsc(s.getCompanyId()).stream()
                .map(PageRow::of).toList();
        return new PortalInfo(s.getSlug(), s.getBrandName(), s.getLogoUrl(), s.getFaviconUrl(),
                settingsService.content(s), pageRows, ticketCategories.listOf(s.getCompanyId()),
                kb.categoriesOf(s.getCompanyId()), ticketFields.activeOf(s.getCompanyId()));
    }

    @Transactional(readOnly = true)
    public PageDto page(String slug, String pageSlug) {
        PortalSettings s = portal(slug);
        return pages.findByCompanyIdAndSlugAndDeletedFalse(s.getCompanyId(), pageSlug).map(PageDto::of)
                .orElseThrow(() -> ApiException.notFound(ESupportError.SUP_026));
    }

    @Transactional(readOnly = true)
    public ResponseListDto<KnowledgeService.ArticleRow> articles(String slug, Long categoryId, String keyword,
                                                                Pageable pageable) {
        return ResponseListDto.of(kb.listOf(portal(slug).getCompanyId(), true, categoryId, keyword, pageable));
    }

    @Transactional(readOnly = true)
    public KnowledgeService.ArticleDto article(String slug, Long id) {
        return kb.getPublished(portal(slug).getCompanyId(), id);
    }

    @Transactional(readOnly = true)
    public ResponseListDto<FaqService.FaqDto> faqs(String slug, String keyword, Pageable pageable) {
        return ResponseListDto.of(faqs.listOf(portal(slug).getCompanyId(), true, keyword, pageable));
    }

    /** Always CUSTOM and IN_PROGRESS (the reference portal forces account_type custom). */
    @Transactional
    public PublicTicketCreated createTicket(String slug, PublicTicketForm f) {
        PortalSettings s = portal(slug);
        java.util.Map<Long, String> custom = ticketFields.validate(s.getCompanyId(), f.customFields());
        TicketService.Created c = tickets.open(s.getCompanyId(), Ticket.EAccountType.CUSTOM, f.name(), f.email(),
                f.categoryId(), f.subject(), f.description(), EStatus.IN_PROGRESS, List.of(), true);
        ticketFields.store(s.getCompanyId(), c.ticket().getId(), custom);
        return new PublicTicketCreated(c.ticket().getTicketNumber(), c.trackingToken(), c.ticket().getStatus());
    }

    @Transactional(readOnly = true)
    public PublicTicketView track(String slug, TrackForm f) {
        PortalSettings s = portal(slug);
        return view(find(s.getCompanyId(), f.ticketNumber(), f.email(), f.trackingToken()));
    }

    /** The customer's reply; refused on a CLOSED ticket (SUP_006). */
    @Transactional
    public PublicTicketView reply(String slug, PublicReplyForm f) {
        PortalSettings s = portal(slug);
        Ticket t = find(s.getCompanyId(), f.ticketNumber(), f.email(), f.trackingToken());
        tickets.addReply(t, ESender.CUSTOMER, t.getName(), f.description(), List.of());
        return view(t);
    }

    @Transactional
    public void contact(String slug, ContactService.ContactForm f) {
        contacts.submit(portal(slug).getCompanyId(), f);
    }

    // ----

    private PortalSettings portal(String slug) {
        return settings.findBySlug(slug == null ? "" : slug.toLowerCase(java.util.Locale.ROOT))
                .filter(PortalSettings::isEnabled)
                .orElseThrow(() -> ApiException.notFound(ESupportError.SUP_009));
    }

    Ticket find(Long companyId, String number, String email, String token) {
        String tk = Texts.blankToNull(token);
        if (tk != null) {
            return ticketRepo.findByCompanyIdAndTrackingTokenHashAndDeletedFalse(companyId, TrackingTokens.hash(tk))
                    .orElseThrow(PublicSupportService::notFound);
        }
        String n = Texts.blankToNull(number);
        String e = Texts.email(Texts.blankToNull(email));
        if (n == null || e == null) {
            throw notFound();
        }
        return ticketRepo.findByCompanyIdAndTicketNumberAndDeletedFalse(companyId, n.toUpperCase(java.util.Locale.ROOT))
                .filter(t -> Objects.equals(t.getEmail(), e))
                .orElseThrow(PublicSupportService::notFound);
    }

    private static ApiException notFound() {
        return ApiException.notFound(ESupportError.SUP_010);
    }

    private PublicTicketView view(Ticket t) {
        List<PublicReply> replies = tickets.conversation(t).stream()
                .map(r -> new PublicReply(r.senderType(), r.senderType() == ESender.STAFF ? null : r.senderName(),
                        r.description(), names(r.attachments()), r.createdDate()))
                .toList();
        return new PublicTicketView(t.getTicketNumber(), t.getName(), t.getSubject(), tickets.categoryName(t),
                t.getStatus(), t.getDescription(), names(tickets.attachmentsOf(t)), t.getCreatedDate(),
                t.getUpdatedDate(), replies, tickets.customFieldsOf(t));
    }

    /** File names only: downloading needs a media-service link, which the portal has no token for. */
    private static List<String> names(List<Attachment> list) {
        return list.stream().map(Attachment::name).toList();
    }
}
