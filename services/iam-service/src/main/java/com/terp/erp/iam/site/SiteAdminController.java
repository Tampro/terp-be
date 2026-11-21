package com.terp.erp.iam.site;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.iam.site.SiteService.InquiryDto;
import com.terp.erp.iam.site.SiteService.PageDto;
import com.terp.erp.iam.site.SiteService.PageForm;
import com.terp.erp.iam.site.SiteService.PageRow;
import com.terp.erp.iam.site.SiteService.ReplyForm;
import com.terp.erp.iam.site.SiteService.StatusForm;
import com.terp.erp.iam.site.SiteService.SubscriberDto;

import java.util.Map;

@Tag(name = "Website (super admin)", description = "Landing Page, Custom Pages, Contact Inquiries, Newsletter")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/admin/site")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@PermissionChecker.isSuperAdmin()")
public class SiteAdminController {
    private final SiteService service;
    private final CookieConsentService consents;

    @Operation(summary = "Download Accepted Cookies (CSV)", description = "Consent choices of the period, UTC dates, "
            + "both included; default the last 30 days, at most 366. Columns: consented_at, choice, categories, ip "
            + "(shortened).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Period reversed or too long (SITE_007)"))
    @GetMapping("/cookie-consents/export")
    public org.springframework.http.ResponseEntity<byte[]> cookieConsents(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cookie-consents.csv\"")
                .body(consents.csv(from, to));
    }

    // ---- landing page

    @Operation(summary = "Landing Page Settings: every section", description = "Keys: general, order, advanced, "
            + "header, hero, footer, features, screenshots, whyUs, about, team, reviews, plans, faq, newsletter, "
            + "contact. null = never saved. Each is the FE's JSON document; images are media URLs.")
    @GetMapping("/landing")
    public ApiMessageDto<Map<String, JsonNode>> landing() {
        return ApiMessageDto.ok(service.sections());
    }

    @Operation(summary = "Save one section", description = "Full replacement of the section's JSON object (max "
            + "64 KB). order = {\"items\":[{\"key\":\"hero\",\"enabled\":true},...]}. advanced.customJs is stored "
            + "but published only when the operator sets ERP_LANDING_CUSTOM_JS=true.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not a JSON object or over 64 KB (SITE_002)"),
            @ApiResponse(responseCode = "404", description = "Unknown section (SITE_001)")})
    @PutMapping("/landing/{key}")
    public ApiMessageDto<JsonNode> saveSection(@PathVariable String key, @RequestBody JsonNode content) {
        return ApiMessageDto.ok(service.saveSection(key, content));
    }

    // ---- custom pages

    @Operation(summary = "Custom Pages", description = "Search by title.")
    @GetMapping("/pages")
    public ApiMessageDto<ResponseListDto<PageRow>> pages(@RequestParam(required = false) String keyword,
                                                         @ParameterObject @PageableDefault(size = 20, sort = "sortOrder")
                                                         Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.searchPages(keyword, pageable)));
    }

    @Operation(summary = "One custom page")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such page (SITE_004)"))
    @GetMapping("/pages/{id}")
    public ApiMessageDto<PageDto> page(@PathVariable Long id) {
        return ApiMessageDto.ok(service.page(id));
    }

    @Operation(summary = "Add Page", description = "The slug comes from the title (accents removed); the content "
            + "is sanitized (no script, event handler or javascript: link).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Validation"))
    @PostMapping("/pages")
    public ApiMessageDto<PageDto> createPage(@Valid @RequestBody PageForm form) {
        return ApiMessageDto.ok(service.createPage(form));
    }

    @Operation(summary = "Edit a page", description = "Full replacement; a new title gives a new slug.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Validation"),
            @ApiResponse(responseCode = "404", description = "No such page (SITE_004)")})
    @PutMapping("/pages/{id}")
    public ApiMessageDto<PageDto> updatePage(@PathVariable Long id, @Valid @RequestBody PageForm form) {
        return ApiMessageDto.ok(service.updatePage(id, form));
    }

    @Operation(summary = "Delete a page permanently")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such page (SITE_004)"))
    @DeleteMapping("/pages/{id}")
    public ApiMessageDto<Void> deletePage(@PathVariable Long id) {
        service.deletePage(id);
        return ApiMessageDto.ok(null);
    }

    // ---- contact inquiries

    @Operation(summary = "Contact Inquiries", description = "Search by name, email or subject; newest first.")
    @GetMapping("/inquiries")
    public ApiMessageDto<ResponseListDto<InquiryDto>> inquiries(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ContactInquiry.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.searchInquiries(keyword, status, pageable)));
    }

    @Operation(summary = "View an inquiry")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such inquiry (SITE_005)"))
    @GetMapping("/inquiries/{id}")
    public ApiMessageDto<InquiryDto> inquiry(@PathVariable Long id) {
        return ApiMessageDto.ok(service.inquiry(id));
    }

    @Operation(summary = "Update Status", description = "NEW, CONTACTED, QUALIFIED, CONVERTED or CLOSED.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such inquiry (SITE_005)"))
    @PutMapping("/inquiries/{id}/status")
    public ApiMessageDto<InquiryDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Send Reply", description = "Plain-text email to the inquirer through the platform SMTP; "
            + "a NEW inquiry becomes CONTACTED.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Platform SMTP not configured (SETTINGS_003), sending "
                    + "failed with the server's reason (SITE_008)"),
            @ApiResponse(responseCode = "404", description = "No such inquiry (SITE_005)")})
    @PostMapping("/inquiries/{id}/reply")
    public ApiMessageDto<InquiryDto> reply(@PathVariable Long id, @Valid @RequestBody ReplyForm form) {
        return ApiMessageDto.ok(service.reply(id, form));
    }

    @Operation(summary = "Delete an inquiry permanently")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such inquiry (SITE_005)"))
    @DeleteMapping("/inquiries/{id}")
    public ApiMessageDto<Void> deleteInquiry(@PathVariable Long id) {
        service.deleteInquiry(id);
        return ApiMessageDto.ok(null);
    }

    // ---- newsletter

    @Operation(summary = "Newsletter subscribers", description = "Search by email; newest first.")
    @GetMapping("/newsletter")
    public ApiMessageDto<ResponseListDto<SubscriberDto>> subscribers(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.subscribers(keyword, pageable)));
    }

    @Operation(summary = "Remove a subscriber")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such subscriber (SITE_006)"))
    @DeleteMapping("/newsletter/{id}")
    public ApiMessageDto<Void> unsubscribe(@PathVariable Long id) {
        service.unsubscribe(id);
        return ApiMessageDto.ok(null);
    }
}
