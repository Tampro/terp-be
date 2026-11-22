package com.terp.erp.support.ticket;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.support.ticket.TicketFieldService.FieldDto;
import com.terp.erp.support.ticket.TicketFieldService.FieldForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@Tag(name = "Support ticket fields", description = "Custom fields of the ticket form (admin and portal)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support/ticket-fields")
@SecurityRequirement(name = "bearerAuth")
public class TicketFieldController {
    private final TicketFieldService service;

    @GetMapping
    @PreAuthorize("@PermissionChecker.has('SUPPORT_TICKET_VIEW')")
    @Operation(summary = "Ticket fields of the company (active and inactive), in display order")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_001")})
    public ApiMessageDto<List<FieldDto>> list(@RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.list(companyId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_TICKET_VIEW')")
    @Operation(summary = "One ticket field")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_042")})
    public ApiMessageDto<FieldDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("@PermissionChecker.has('SUPPORT_SETTINGS_MANAGE')")
    @Operation(summary = "Create a ticket field")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_001, SUP_040 (SELECT without options), "
                    + "SUP_041 (width)")})
    public ApiMessageDto<FieldDto> create(@RequestParam(required = false) Long companyId,
                                          @Valid @RequestBody FieldForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_SETTINGS_MANAGE')")
    @Operation(summary = "Replace a ticket field (full replacement)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_040, SUP_041"),
            @ApiResponse(responseCode = "404", description = "SUP_042")})
    public ApiMessageDto<FieldDto> update(@PathVariable Long id, @Valid @RequestBody FieldForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_SETTINGS_MANAGE')")
    @Operation(summary = "Delete a ticket field (soft); tickets keep their values")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_042")})
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
