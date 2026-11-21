package com.terp.erp.iam.helpdesk;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import com.terp.erp.iam.helpdesk.HelpdeskDtos.CategoryDto;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.CategoryForm;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.HelpdeskStats;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.ReplyDto;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.ReplyForm;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.TicketCreateForm;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.TicketDetail;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.TicketRow;
import com.terp.erp.iam.helpdesk.HelpdeskDtos.TicketUpdateForm;

import java.util.List;
import java.util.Map;

/**
 * Platform helpdesk: companies → platform support. Company users need HELPDESK_VIEW / HELPDESK_CREATE and only ever
 * see their own company's tickets; managing tickets and categories is super-admin only.
 */
@Tag(name = "Platform helpdesk")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/helpdesk")
@SecurityRequirement(name = "bearerAuth")
public class HelpdeskController {
    private static final String COMPANY_PARAM = "Super admin only: one company's tickets (omit = all companies). "
            + "Ignored for company users.";
    private final HelpdeskService service;

    @Operation(summary = "Tickets", description = "A company user sees their company's tickets; the super admin "
            + "every company's. Default sort: newest first.")
    @PreAuthorize("@PermissionChecker.has('HELPDESK_VIEW')")
    @GetMapping("/tickets")
    public ApiMessageDto<ResponseListDto<TicketRow>> tickets(
            @Parameter(description = COMPANY_PARAM) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) EHelpdeskStatus status,
            @RequestParam(required = false) EHelpdeskPriority priority,
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Matches the title or the ticket number") @RequestParam(required = false)
            String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, status, priority, categoryId, keyword, pageable)));
    }

    @Operation(summary = "Ticket counts per status (for the status tabs)")
    @PreAuthorize("@PermissionChecker.has('HELPDESK_VIEW')")
    @GetMapping("/tickets/status-counts")
    public ApiMessageDto<Map<EHelpdeskStatus, Long>> statusCounts(
            @Parameter(description = COMPANY_PARAM) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.statusCounts(companyId));
    }

    @Operation(summary = "Ticket with its conversation",
            description = "Internal replies are returned to the super admin only.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such ticket in your company (HDK_001)"))
    @PreAuthorize("@PermissionChecker.has('HELPDESK_VIEW')")
    @GetMapping("/tickets/{id}")
    public ApiMessageDto<TicketDetail> ticket(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Open a ticket", description = "A company user opens it for their own company (status OPEN, "
            + "priority MEDIUM); the super admin names the company and may set the priority.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Super admin without a valid companyId (HDK_008), "
                    + "category missing (HDK_002) or inactive (HDK_003), validation (COMMON_001)"),
            @ApiResponse(responseCode = "403", description = "A company user sent a priority (HDK_009)")})
    @PreAuthorize("@PermissionChecker.has('HELPDESK_CREATE')")
    @PostMapping("/tickets")
    public ApiMessageDto<TicketDetail> create(@Valid @RequestBody TicketCreateForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a ticket (super admin)", description = "Full replacement of title, description, "
            + "category, status and priority. RESOLVED sets resolvedAt; OPEN or IN_PROGRESS clears it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Category missing (HDK_002) or inactive (HDK_003), "
                    + "validation (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = "No such ticket (HDK_001)")})
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/tickets/{id}")
    public ApiMessageDto<TicketDetail> update(@PathVariable Long id, @Valid @RequestBody TicketUpdateForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a ticket (super admin)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such ticket (HDK_001)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @DeleteMapping("/tickets/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Reply to a ticket", description = "Attachments are media files of the ticket's company. "
            + "internal = true is a super admin note the company does not see. A reply does not change the status.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Ticket closed (HDK_004), attachment refused (HDK_007), "
                    + "validation (COMMON_001)"),
            @ApiResponse(responseCode = "403", description = "Internal reply by a company user (HDK_005)"),
            @ApiResponse(responseCode = "404", description = "No such ticket in your company (HDK_001)"),
            @ApiResponse(responseCode = "503", description = "media-service unreachable (IAM_025)")})
    @PreAuthorize("@PermissionChecker.has('HELPDESK_CREATE')")
    @PostMapping("/tickets/{id}/replies")
    public ApiMessageDto<ReplyDto> reply(@PathVariable Long id, @Valid @RequestBody ReplyForm form) {
        return ApiMessageDto.ok(service.reply(id, form));
    }

    @Operation(summary = "Delete a message of the conversation (super admin)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such ticket (HDK_001) or reply (HDK_006)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @DeleteMapping("/tickets/{id}/replies/{replyId}")
    public ApiMessageDto<Void> deleteReply(@PathVariable Long id, @PathVariable Long replyId) {
        service.deleteReply(id, replyId);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Categories", description = "Super admin: all; a company user: the active ones.")
    @PreAuthorize("@PermissionChecker.has('HELPDESK_VIEW')")
    @GetMapping("/categories")
    public ApiMessageDto<List<CategoryDto>> categories() {
        return ApiMessageDto.ok(service.categories());
    }

    @Operation(summary = "Create a category (super admin)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Validation (COMMON_001)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PostMapping("/categories")
    public ApiMessageDto<CategoryDto> createCategory(@Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.createCategory(form));
    }

    @Operation(summary = "Edit a category (super admin)", description = "Full replacement.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Validation (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = "No such category (HDK_002)")})
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/categories/{id}")
    public ApiMessageDto<CategoryDto> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.updateCategory(id, form));
    }

    @Operation(summary = "Delete a category (super admin)", description = "Tickets keep showing its name.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such category (HDK_002)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @DeleteMapping("/categories/{id}")
    public ApiMessageDto<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Dashboard figures (super admin)",
            description = "Tickets created vs resolved per month of the year (UTC) and the current status split.")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/stats")
    public ApiMessageDto<HelpdeskStats> stats(@RequestParam(required = false) Integer year) {
        return ApiMessageDto.ok(service.stats(year));
    }
}
