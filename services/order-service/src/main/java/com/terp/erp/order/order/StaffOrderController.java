package com.terp.erp.order.order;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.exception.ApiException;
import com.terp.erp.order.common.EOrderError;
import com.terp.erp.order.order.OrderDtos.OrderDto;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Back Office view of customer orders (staff realm, ORDER_VIEW) and cancel of a paid one (ORDER_CANCEL); the phone is masked. */
@Tag(name = "Orders (Back Office)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/orders")
@SecurityRequirement(name = "bearerAuth")
public class StaffOrderController {
    private final CustomerOrderRepository orders;
    private final OrderService service;

    @Operation(summary = "Orders of the company's stores", description = "Filters optional; from inclusive, to exclusive (UTC).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ORD_018)"))
    @PreAuthorize("@PermissionChecker.has('ORDER_VIEW')")
    @Transactional(readOnly = true)
    @GetMapping
    public ApiMessageDto<ResponseListDto<OrderDto>> list(
            @Parameter(description = "Super admin only: the company to act on") @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long storeId, @RequestParam(required = false) EOrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Long company = TenantGuard.resolveCompany(companyId, EOrderError.ORD_018);
        return ApiMessageDto.ok(ResponseListDto.of(orders.search(company, storeId, status, from, to, pageable)
                .map(service::staffDto)));
    }

    @Operation(summary = "An order of the company")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such order in your company (ORD_018)"))
    @PreAuthorize("@PermissionChecker.has('ORDER_VIEW')")
    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public ApiMessageDto<OrderDto> get(@PathVariable Long id) {
        CustomerOrder o = orders.findById(id).orElseThrow(() -> ApiException.notFound(EOrderError.ORD_018));
        TenantGuard.assertSameCompany(o.getCompanyId(), EOrderError.ORD_018);
        return ApiMessageDto.ok(service.staffDto(o));
    }

    public record CancelForm(@Schema(description = "Why the paid order is cancelled (Back Office only, not shown to the customer)",
            example = "Out of stock at packing", requiredMode = Schema.RequiredMode.REQUIRED)
                             @NotBlank @Size(max = 200) String reason) {
    }

    @Operation(summary = "Cancel a paid order and refund it in full", description = "Only before the rider picks it "
            + "up: the delivery is cancelled, the stock goes back, coupon use and points are given back, a reversal "
            + "journal (SHOP_ORDER_REFUND) is requested and the customer is emailed (ORDER_REFUNDED). Refund is sandbox "
            + "(no PG call). A failure leaves the order PAID; retrying is safe.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order REFUNDED"),
            @ApiResponse(responseCode = "400", description = "ORD_019: not PAID; ORD_020: already picked up; "
                    + "COMMON_001: reason missing or over 200"),
            @ApiResponse(responseCode = "403", description = "COMMON_004: without ORDER_CANCEL"),
            @ApiResponse(responseCode = "404", description = "ORD_018: no such order in your company"),
            @ApiResponse(responseCode = "503", description = "ORD_007: delivery or inventory unavailable, nothing changed")})
    @PreAuthorize("@PermissionChecker.has('ORDER_CANCEL')")
    @PostMapping("/{id}/cancel")
    public ApiMessageDto<OrderDto> cancel(@PathVariable Long id, @Valid @RequestBody CancelForm form) {
        return ApiMessageDto.ok(service.refund(id, form.reason()));
    }
}
