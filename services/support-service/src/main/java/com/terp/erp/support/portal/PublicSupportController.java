package com.terp.erp.support.portal;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.support.contact.ContactService.ContactForm;
import com.terp.erp.support.kb.FaqService.FaqDto;
import com.terp.erp.support.kb.KnowledgeService.ArticleDto;
import com.terp.erp.support.kb.KnowledgeService.ArticleRow;
import com.terp.erp.support.portal.PortalSettingsService.PageDto;
import com.terp.erp.support.portal.PublicSupportService.PortalInfo;
import com.terp.erp.support.portal.PublicSupportService.PublicReplyForm;
import com.terp.erp.support.portal.PublicSupportService.PublicTicketCreated;
import com.terp.erp.support.portal.PublicSupportService.PublicTicketForm;
import com.terp.erp.support.portal.PublicSupportService.PublicTicketView;
import com.terp.erp.support.portal.PublicSupportService.TrackForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No token: the portal slug picks the company. The gateway permits exactly these (GET, and POST on tickets,
 * tickets/track, tickets/reply, contact) and rate-limits them per IP. Track and reply are POSTs so the email and the
 * tracking token stay out of URLs and access logs.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/support/{slug}")
@Tag(name = "Public support portal", description = "Customer support portal of a company (no login)")
public class PublicSupportController {
    private final PublicSupportService service;

    @GetMapping
    @Operation(summary = "Portal: branding, sections, support and contact information, quick links, pages, categories")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_009 (unknown slug or portal disabled)")})
    public ApiMessageDto<PortalInfo> info(@PathVariable String slug) {
        return ApiMessageDto.ok(service.info(slug));
    }

    @GetMapping("/pages/{pageSlug}")
    @Operation(summary = "A custom page")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_009, SUP_026")})
    public ApiMessageDto<PageDto> page(@PathVariable String slug, @PathVariable String pageSlug) {
        return ApiMessageDto.ok(service.page(slug, pageSlug));
    }

    @GetMapping("/kb")
    @Operation(summary = "Published knowledge base articles (summary only)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_009")})
    public ApiMessageDto<ResponseListDto<ArticleRow>> articles(@PathVariable String slug,
                                                               @RequestParam(required = false) Long categoryId,
                                                               @RequestParam(required = false) String keyword,
                                                               @ParameterObject Pageable pageable) {
        return ApiMessageDto.ok(service.articles(slug, categoryId, keyword, pageable));
    }

    @GetMapping("/kb/{id}")
    @Operation(summary = "A published knowledge base article with its content")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_009, SUP_015")})
    public ApiMessageDto<ArticleDto> article(@PathVariable String slug, @PathVariable Long id) {
        return ApiMessageDto.ok(service.article(slug, id));
    }

    @GetMapping("/faqs")
    @Operation(summary = "Published FAQs")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_009")})
    public ApiMessageDto<ResponseListDto<FaqDto>> faqs(@PathVariable String slug,
                                                       @RequestParam(required = false) String keyword,
                                                       @ParameterObject Pageable pageable) {
        return ApiMessageDto.ok(service.faqs(slug, keyword, pageable));
    }

    @PostMapping("/tickets")
    @Operation(summary = "Open a ticket; answers its number and tracking token (shown once)",
            description = "No attachments on the portal. Account type CUSTOM, status IN_PROGRESS.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_005 (category), SUP_034"),
            @ApiResponse(responseCode = "404", description = "SUP_009"),
            @ApiResponse(responseCode = "429", description = "COMMON_006 (gateway rate limit)")})
    public ApiMessageDto<PublicTicketCreated> create(@PathVariable String slug,
                                                     @Valid @RequestBody PublicTicketForm form) {
        return ApiMessageDto.ok(service.createTicket(slug, form));
    }

    @PostMapping("/tickets/track")
    @Operation(summary = "Track a ticket by number + email, or by tracking token",
            description = "Answers status and the public conversation (no internal note).")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001"),
            @ApiResponse(responseCode = "404", description = "SUP_009, SUP_010 (no match: never says which part)"),
            @ApiResponse(responseCode = "429", description = "COMMON_006")})
    public ApiMessageDto<PublicTicketView> track(@PathVariable String slug, @Valid @RequestBody TrackForm form) {
        return ApiMessageDto.ok(service.track(slug, form));
    }

    @PostMapping("/tickets/reply")
    @Operation(summary = "Reply to one's own ticket (number + email, or tracking token); refused when closed")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_006 (closed), SUP_034"),
            @ApiResponse(responseCode = "404", description = "SUP_009, SUP_010"),
            @ApiResponse(responseCode = "429", description = "COMMON_006")})
    public ApiMessageDto<PublicTicketView> reply(@PathVariable String slug, @Valid @RequestBody PublicReplyForm form) {
        return ApiMessageDto.ok(service.reply(slug, form));
    }

    @PostMapping("/contact")
    @Operation(summary = "Send the contact form")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001"),
            @ApiResponse(responseCode = "404", description = "SUP_009"),
            @ApiResponse(responseCode = "429", description = "COMMON_006")})
    public ApiMessageDto<Void> contact(@PathVariable String slug, @Valid @RequestBody ContactForm form) {
        service.contact(slug, form);
        return ApiMessageDto.ok(null);
    }
}
