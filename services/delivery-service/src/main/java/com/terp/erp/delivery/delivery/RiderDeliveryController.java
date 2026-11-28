package com.terp.erp.delivery.delivery;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.delivery.delivery.DeliveryDtos.DeliveryDetailDto;
import com.terp.erp.delivery.delivery.DeliveryDtos.DeliveryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in rider's own deliveries (assigned to the token subject, same company). */
@Tag(name = "Deliveries (rider)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/delivery/my-deliveries")
@SecurityRequirement(name = "bearerAuth")
public class RiderDeliveryController {
    private static final String MINE = "Not a delivery assigned to you (DLV_001)";
    private static final String STATE = "Not allowed in the delivery's status (DLV_005)";

    private final DeliveryService service;

    @Operation(summary = "My deliveries", description = "Newest first.")
    @PreAuthorize("@PermissionChecker.has('DELIVERY_RIDE')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<DeliveryDto>> list(@RequestParam(required = false) EDeliveryStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.mine(status, pageable)));
    }

    @Operation(summary = "One of my deliveries", description = "With the customer's phone in full, to call them.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = MINE),
            @ApiResponse(responseCode = "503", description = "order-service unavailable (DLV_003)")})
    @PreAuthorize("@PermissionChecker.has('DELIVERY_RIDE')")
    @GetMapping("/{id}")
    public ApiMessageDto<DeliveryDetailDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.myDelivery(id));
    }

    @Operation(summary = "Picked up at the store", description = "ASSIGNED → PICKED_UP.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = STATE),
            @ApiResponse(responseCode = "404", description = MINE),
            @ApiResponse(responseCode = "409", description = "Changed at the same time (DLV_006)")})
    @PreAuthorize("@PermissionChecker.has('DELIVERY_RIDE')")
    @PostMapping("/{id}/pick-up")
    public ApiMessageDto<DeliveryDto> pickUp(@PathVariable Long id) {
        return ApiMessageDto.ok(service.pickUp(id));
    }

    @Operation(summary = "Delivered to the customer", description = "PICKED_UP → DELIVERED.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = STATE),
            @ApiResponse(responseCode = "404", description = MINE),
            @ApiResponse(responseCode = "409", description = "Changed at the same time (DLV_006)")})
    @PreAuthorize("@PermissionChecker.has('DELIVERY_RIDE')")
    @PostMapping("/{id}/deliver")
    public ApiMessageDto<DeliveryDto> deliver(@PathVariable Long id) {
        return ApiMessageDto.ok(service.deliver(id));
    }
}
