package com.terp.erp.delivery.delivery;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.delivery.delivery.DeliveryDtos.AssignForm;
import com.terp.erp.delivery.delivery.DeliveryDtos.CancelForm;
import com.terp.erp.delivery.delivery.DeliveryDtos.DeliveryDetailDto;
import com.terp.erp.delivery.delivery.DeliveryDtos.DeliveryDto;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Deliveries (Back Office)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/delivery/deliveries")
@SecurityRequirement(name = "bearerAuth")
public class DeliveryController {
    private static final String NOT_FOUND = "No such delivery in your company (DLV_001)";
    private static final String STATE = "Not allowed in the delivery's status (DLV_005)";
    private static final String CONFLICT = "Changed by someone else at the same time, reload (DLV_006)";

    private final DeliveryService service;

    @Operation(summary = "Deliveries of the company's stores", description = "Created READY when an order is paid; "
            + "filters optional; newest first.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (DLV_002)"))
    @PreAuthorize("@PermissionChecker.has('DELIVERY_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<DeliveryDto>> list(
            @Parameter(description = "Super admin only: the company to act on") @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long storeId, @RequestParam(required = false) EDeliveryStatus status,
            @Parameter(description = "Keycloak subject of the rider") @RequestParam(required = false) String riderSub,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(companyId, storeId, status, riderSub, pageable)));
    }

    @Operation(summary = "A delivery with its destination", description = "Destination read from the order; the "
            + "customer's phone is masked.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = NOT_FOUND),
            @ApiResponse(responseCode = "503", description = "order-service unavailable (DLV_003)")})
    @PreAuthorize("@PermissionChecker.has('DELIVERY_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<DeliveryDetailDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Assign (or re-assign) a rider", description = "READY / ASSIGNED → ASSIGNED.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Rider not an active user of the company or without "
            + "DELIVERY_RIDE (DLV_004), " + STATE + ", invalid field (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND),
            @ApiResponse(responseCode = "409", description = CONFLICT),
            @ApiResponse(responseCode = "503", description = "IAM unavailable (DLV_003)")})
    @PreAuthorize("@PermissionChecker.has('DELIVERY_MANAGE')")
    @PostMapping("/{id}/assign")
    public ApiMessageDto<DeliveryDto> assign(@PathVariable Long id, @Valid @RequestBody AssignForm form) {
        return ApiMessageDto.ok(service.assign(id, form.riderUserId()));
    }

    @Operation(summary = "Cancel before pick-up", description = "READY / ASSIGNED → CANCELLED (the order itself is not "
            + "refunded here).")
    @ApiResponses({@ApiResponse(responseCode = "400", description = STATE + ", invalid field (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND),
            @ApiResponse(responseCode = "409", description = CONFLICT)})
    @PreAuthorize("@PermissionChecker.has('DELIVERY_MANAGE')")
    @PostMapping("/{id}/cancel")
    public ApiMessageDto<DeliveryDto> cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelForm form) {
        return ApiMessageDto.ok(service.cancel(id, form == null ? null : form.reason()));
    }
}
