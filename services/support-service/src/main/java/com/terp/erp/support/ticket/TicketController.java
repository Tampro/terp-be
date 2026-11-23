package com.terp.erp.support.ticket;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.support.ticket.Ticket.EAccountType;
import com.terp.erp.support.ticket.Ticket.EStatus;
import com.terp.erp.support.ticket.TicketCategoryService.CategoryDto;
import com.terp.erp.support.ticket.TicketCategoryService.CategoryForm;
import com.terp.erp.support.ticket.TicketService.CreatedTicketDto;
import com.terp.erp.support.ticket.TicketService.ReplyDto;
import com.terp.erp.support.ticket.TicketService.ReplyForm;
import com.terp.erp.support.ticket.TicketService.StatusCounts;
import com.terp.erp.support.ticket.TicketService.StatusForm;
import com.terp.erp.support.ticket.TicketService.TicketDto;
import com.terp.erp.support.ticket.TicketService.TicketForm;
import com.terp.erp.support.ticket.TicketService.TicketRow;
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

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support")
@Tag(name = "Support tickets", description = "Tickets, their conversation and ticket categories (ERP manual 18)")
@SecurityRequirement(name = "bearerAuth")
public class TicketController {
    private final TicketService service;
    private final TicketCategoryService categoryService;
    private final TicketStatsService statsService;

    @GetMapping("/tickets")
    @PreAuthorize("@PermissionChecker.hasAny('SUPPORT_TICKET_VIEW','SUPPORT_TICKET_VIEW_OWN')")
    @Operation(summary = "List tickets (tabs: status; filters: category, account type, keyword)",
            description = "keyword matches ticket number, subject, name and email. Default order: last update first.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_001 (super admin without companyId)")})
    public ApiMessageDto<ResponseListDto<TicketRow>> list(
            @RequestParam(required = false) Long companyId, @RequestParam(required = false) EStatus status,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) EAccountType accountType,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(sort = "updatedDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.list(companyId, status, categoryId, accountType, keyword, pageable)));
    }

    @GetMapping("/tickets/status-counts")
    @PreAuthorize("@PermissionChecker.hasAny('SUPPORT_TICKET_VIEW','SUPPORT_TICKET_VIEW_OWN')")
    @Operation(summary = "Counts for the tabs All / In Progress / On Hold / Closed")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_001")})
    public ApiMessageDto<StatusCounts> statusCounts(@RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.statusCounts(companyId));
    }

    @GetMapping("/tickets/stats")
    @PreAuthorize("@PermissionChecker.hasAny('SUPPORT_TICKET_VIEW','SUPPORT_TICKET_VIEW_OWN')")
    @Operation(summary = "Support dashboard figures",
            description = "Totals (all time): total / open (not CLOSED) / closed / created today, average first staff "
                    + "response in minutes, status and category split, KB article and FAQ counts of the company; "
                    + "monthly created / closed of the year. SUPPORT_TICKET_VIEW_OWN (or own=true) counts only my "
                    + "tickets. tz = IANA time zone of today and the months, default Asia/Ho_Chi_Minh.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_001, unknown time zone (COMMON_001)")})
    public ApiMessageDto<TicketStatsService.TicketStats> stats(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String tz,
            @RequestParam(defaultValue = "false") boolean own) {
        return ApiMessageDto.ok(statsService.stats(companyId, year, tz, own));
    }

    @GetMapping("/tickets/{id}")
    @PreAuthorize("@PermissionChecker.hasAny('SUPPORT_TICKET_VIEW','SUPPORT_TICKET_VIEW_OWN')")
    @Operation(summary = "A ticket with its internal note and whole conversation (oldest first)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_004 (unknown or another company's ticket)")})
    public ApiMessageDto<TicketDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @PostMapping("/tickets")
    @PreAuthorize("@PermissionChecker.hasAny('SUPPORT_TICKET_MANAGE','SUPPORT_TICKET_MANAGE_OWN')")
    @Operation(summary = "Create a ticket; answers the tracking token once",
            description = "Numbered TKT-000001… per company. attachmentFileIds = media-service files of the company.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_001, SUP_005, SUP_007, SUP_008, SUP_034, custom fields SUP_035-SUP_039"),
            @ApiResponse(responseCode = "503", description = "SUP_002 (media-service unreachable)")})
    public ApiMessageDto<CreatedTicketDto> create(@RequestParam(required = false) Long companyId,
                                                  @Valid @RequestBody TicketForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @PutMapping("/tickets/{id}")
    @PreAuthorize("@PermissionChecker.hasAny('SUPPORT_TICKET_MANAGE','SUPPORT_TICKET_MANAGE_OWN')")
    @Operation(summary = "Replace a ticket",
            description = "Full replacement of account type, name, email, category, subject, description, attachments "
                    + "and note; status null keeps the current status. Number and tracking token never change.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_005, SUP_007, SUP_008, SUP_034, custom fields SUP_035-SUP_039"),
            @ApiResponse(responseCode = "404", description = "SUP_004"),
            @ApiResponse(responseCode = "503", description = "SUP_002")})
    public ApiMessageDto<TicketDto> update(@PathVariable Long id, @Valid @RequestBody TicketForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @PutMapping("/tickets/{id}/status")
    @PreAuthorize("@PermissionChecker.hasAny('SUPPORT_TICKET_MANAGE','SUPPORT_TICKET_MANAGE_OWN')")
    @Operation(summary = "Change the status (IN_PROGRESS, ON_HOLD, CLOSED); a closed ticket can be reopened")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001"),
            @ApiResponse(responseCode = "404", description = "SUP_004")})
    public ApiMessageDto<TicketRow> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form));
    }

    @DeleteMapping("/tickets/{id}")
    @PreAuthorize("@PermissionChecker.hasAny('SUPPORT_TICKET_MANAGE','SUPPORT_TICKET_MANAGE_OWN')")
    @Operation(summary = "Delete a ticket (soft); its portal tracking stops working")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_004")})
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @PostMapping("/tickets/{id}/replies")
    @PreAuthorize("@PermissionChecker.hasAny('SUPPORT_TICKET_MANAGE','SUPPORT_TICKET_MANAGE_OWN')")
    @Operation(summary = "Staff reply; refused when the ticket is closed")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_006 (closed), SUP_007, SUP_008, SUP_034"),
            @ApiResponse(responseCode = "404", description = "SUP_004"),
            @ApiResponse(responseCode = "503", description = "SUP_002")})
    public ApiMessageDto<ReplyDto> reply(@PathVariable Long id, @Valid @RequestBody ReplyForm form) {
        return ApiMessageDto.ok(service.reply(id, form));
    }

    @GetMapping("/ticket-categories")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_TICKET_VIEW')")
    @Operation(summary = "Ticket categories of the company, by name")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_001")})
    public ApiMessageDto<List<CategoryDto>> categories(@RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(categoryService.list(companyId));
    }

    @PostMapping("/ticket-categories")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_TICKET_MANAGE')")
    @Operation(summary = "Create a ticket category")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_001")})
    public ApiMessageDto<CategoryDto> createCategory(@RequestParam(required = false) Long companyId,
                                                     @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(categoryService.create(companyId, form));
    }

    @PutMapping("/ticket-categories/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_TICKET_MANAGE')")
    @Operation(summary = "Replace a ticket category (full replacement: name and color)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001"),
            @ApiResponse(responseCode = "404", description = "SUP_005")})
    public ApiMessageDto<CategoryDto> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(categoryService.update(id, form));
    }

    @DeleteMapping("/ticket-categories/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_TICKET_MANAGE')")
    @Operation(summary = "Delete a ticket category (soft); refused while tickets use it")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_013"),
            @ApiResponse(responseCode = "404", description = "SUP_005")})
    public ApiMessageDto<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiMessageDto.ok(null);
    }
}
