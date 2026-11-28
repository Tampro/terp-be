package com.terp.erp.delivery.delivery;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.delivery.delivery.DeliveryDtos.DeliveryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service only (internal chain, azp allow-list): no user, the company comes from the caller. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/delivery/orders")
@Tag(name = "Delivery (internal)")
@SecurityRequirement(name = "bearerAuth")
public class InternalDeliveryController {
    private final DeliveryService service;

    public record CancelForm(
            @Schema(description = "Order number, kept on the CANCELLED row when no delivery exists yet",
                    example = "HE-20261128-000001", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 30) String orderNo,
            @Schema(description = "Store of the order", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long storeId,
            @Schema(description = "Warehouse of the order", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long warehouseId) {
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel the delivery of a paid order (order-service)", description = "READY / ASSIGNED -> "
            + "CANCELLED; already CANCELLED -> 200 unchanged; no delivery yet -> a CANCELLED row so a late order.paid "
            + "creates nothing. Only service accounts on the allow-list (erp-order-service).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelled (or already cancelled)"),
            @ApiResponse(responseCode = "403", description = "COMMON_004: not an allowed service account"),
            @ApiResponse(responseCode = "409", description = "DLV_007: already PICKED_UP or DELIVERED; "
                    + "DLV_006: changed meanwhile, retry")})
    public ApiMessageDto<DeliveryDto> cancel(@PathVariable Long orderId, @RequestParam Long companyId,
                                             @Valid @RequestBody CancelForm form) {
        return ApiMessageDto.ok(service.cancelForOrder(companyId, orderId,
                new DeliveryService.OrderRef(form.orderNo(), form.storeId(), form.warehouseId())));
    }
}
