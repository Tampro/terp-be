package com.terp.erp.order.order;

import com.terp.erp.common.ApiMessageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service only (SecurityConfig internalChain, erp.order.internal.client-ids: delivery-service). */
@Tag(name = "Internal")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/order/orders")
@SecurityRequirement(name = "bearerAuth")
public class InternalOrderController {
    private final OrderService service;

    @Operation(summary = "An order's delivery snapshot (delivery-service)", description = "Plain phone: the caller masks it "
            + "for Back Office and shows it to the assigned rider only.")
    @ApiResponses({@ApiResponse(responseCode = "401", description = "No or invalid token (COMMON_003)"),
            @ApiResponse(responseCode = "403", description = "Token not of an allowed service account (COMMON_004)"),
            @ApiResponse(responseCode = "404", description = "No such order in the company (ORD_018)")})
    @GetMapping("/{id}")
    public ApiMessageDto<InternalOrderDto> get(@PathVariable long id, @RequestParam long companyId) {
        return ApiMessageDto.ok(service.internal(companyId, id));
    }
}
