package com.terp.erp.notification.delivery;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.notification.delivery.Delivery.EChannel;
import com.terp.erp.notification.delivery.Delivery.EStatus;
import com.terp.erp.notification.delivery.DeliveryService.DeliveryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Notification deliveries")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications/deliveries")
@SecurityRequirement(name = "bearerAuth")
public class DeliveryController {
    private final DeliveryService service;

    @Operation(summary = "Delivery log", description = "Newest first by default. companyId: super admin only (a "
            + "company user always sees their own company). from (inclusive) / to (exclusive) filter the creation time.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Super admin without companyId (NTF_001)"),
            @ApiResponse(responseCode = "403", description = "Missing NOTIFICATION_LOG_VIEW (COMMON_004)")})
    @PreAuthorize("@PermissionChecker.has('NOTIFICATION_LOG_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<DeliveryDto>> list(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) EStatus status,
            @RequestParam(required = false) EChannel channel,
            @Parameter(example = "support.ticket.created") @RequestParam(required = false) String eventType,
            @Parameter(example = "2026-11-22T00:00:00Z") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(example = "2026-11-23T00:00:00Z") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(companyId, status, channel, eventType, from, to,
                pageable)));
    }

    @Operation(summary = "One delivery")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Missing NOTIFICATION_LOG_VIEW (COMMON_004)"),
            @ApiResponse(responseCode = "404", description = "No such delivery in the caller's company (NTF_002)")})
    @PreAuthorize("@PermissionChecker.has('NOTIFICATION_LOG_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<DeliveryDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Retry a failed delivery", description = "Sends it again now with a fresh attempt count; "
            + "the answer shows the outcome (SENT, PENDING with a back-off, FAILED or SKIPPED).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not a FAILED delivery (NTF_003)"),
            @ApiResponse(responseCode = "403", description = "Missing NOTIFICATION_LOG_MANAGE (COMMON_004)"),
            @ApiResponse(responseCode = "404", description = "No such delivery in the caller's company (NTF_002)")})
    @PreAuthorize("@PermissionChecker.has('NOTIFICATION_LOG_MANAGE')")
    @PostMapping("/{id}/retry")
    public ApiMessageDto<DeliveryDto> retry(@PathVariable Long id) {
        return ApiMessageDto.ok(service.retry(id));
    }
}
