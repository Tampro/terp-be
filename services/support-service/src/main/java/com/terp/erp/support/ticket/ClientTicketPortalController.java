package com.terp.erp.support.ticket;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.support.ticket.Ticket.EStatus;
import com.terp.erp.support.ticket.TicketPortalService.PortalReplyForm;
import com.terp.erp.support.ticket.TicketPortalService.PortalTicket;
import com.terp.erp.support.ticket.TicketPortalService.PortalTicketForm;
import com.terp.erp.support.ticket.TicketPortalService.PortalTicketRow;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Client portal: my support tickets (created by me or raised with my email); the internal note is never shown. */
@Tag(name = "Client portal — tickets")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/portal/client/tickets")
@SecurityRequirement(name = "bearerAuth")
public class ClientTicketPortalController {
    private static final String NOT_MINE = "Not a ticket of mine (SUP_004)";
    private final TicketPortalService service;

    @Operation(summary = "My tickets", description = "Created by me or raised (also on the public portal) with the "
            + "email of my account; latest activity first by default.")
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<PortalTicketRow>> list(
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "updatedDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(status, pageable)));
    }

    @Operation(summary = "Ticket categories I can choose", description = "Active categories of my company.")
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping("/categories")
    public ApiMessageDto<List<TicketCategoryService.CategoryDto>> categories() {
        return ApiMessageDto.ok(service.categories());
    }

    @Operation(summary = "One of my tickets", description = "With the conversation; staff replies carry no sender name.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_MINE))
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping("/{id}")
    public ApiMessageDto<PortalTicket> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Raise a ticket", description = "Account type CLIENT, status IN_PROGRESS; name and email "
            + "are those of my account (not taken from the body).")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Account without email (SUP_043), category not "
            + "of my company (SUP_005), empty description (SUP_034), custom fields (SUP_035..SUP_039), validation "
            + "(COMMON_001)")})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @PostMapping
    public ApiMessageDto<PortalTicket> create(@Valid @RequestBody PortalTicketForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Reply to one of my tickets", description = "Sender CUSTOMER with the ticket's name.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Ticket CLOSED (SUP_006), empty description "
            + "(SUP_034), validation (COMMON_001)"), @ApiResponse(responseCode = "404", description = NOT_MINE)})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @PostMapping("/{id}/replies")
    public ApiMessageDto<PortalTicket> reply(@PathVariable Long id, @Valid @RequestBody PortalReplyForm form) {
        return ApiMessageDto.ok(service.reply(id, form));
    }
}
