package com.terp.erp.order.order;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.order.order.OrderDtos.OrderDto;
import com.terp.erp.order.order.OrderDtos.PayForm;
import com.terp.erp.order.order.OrderDtos.PlaceForm;
import com.terp.erp.security.CustomerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in customer's orders (customer tokens only; own orders only). */
@Tag(name = "Orders (customer)")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CustomerOrderController {
    private static final String MINE_404 = "Not a member (ORD_001), no such order of yours (ORD_018)";
    private static final String DOWN = "member / store / inventory unavailable (ORD_007)";

    private final OrderService service;

    @Operation(summary = "Order the cart of this store", description = "Creates a PENDING_PAYMENT order with a snapshot of "
            + "prices, fees and the delivery address; the cart is emptied of the bought items only once paid.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Empty cart (ORD_008), item no longer available or sold out "
            + "(ORD_009), minimum not reached (ORD_010), store not taking orders now (ORD_011), address not served by the store "
            + "(ORD_013), an order of this store already waits for payment (ORD_014), invalid field (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = "Not a member (ORD_001), store not of the shop (ORD_005), "
                    + "address not yours (ORD_012)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PostMapping("/api/v1/shop/{slug}/stores/{storeId}/orders")
    public ApiMessageDto<OrderDto> place(@PathVariable String slug, @PathVariable long storeId,
                                         @AuthenticationPrincipal CustomerPrincipal me, @Valid @RequestBody PlaceForm form) {
        return ApiMessageDto.ok(service.place(slug, me.sub(), storeId, form));
    }

    @Operation(summary = "My orders in this shop", description = "Newest first.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Not a member (ORD_001)"))
    @GetMapping("/api/v1/shop/{slug}/orders")
    public ApiMessageDto<ResponseListDto<OrderDto>> list(@PathVariable String slug, @AuthenticationPrincipal CustomerPrincipal me,
                                                         @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(slug, me.sub(), pageable)));
    }

    @Operation(summary = "One of my orders")
    @ApiResponses(@ApiResponse(responseCode = "404", description = MINE_404))
    @GetMapping("/api/v1/shop/{slug}/orders/{orderNo}")
    public ApiMessageDto<OrderDto> get(@PathVariable String slug, @PathVariable String orderNo,
                                       @AuthenticationPrincipal CustomerPrincipal me) {
        return ApiMessageDto.ok(service.get(slug, me.sub(), orderNo));
    }

    @Operation(summary = "Pay (sandbox)", description = "Sandbox only (no real PG yet): APPROVED moves the stock out once and "
            + "marks the order PAID; DECLINED marks it PAYMENT_FAILED. Off in production (404).")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not waiting for payment (ORD_016), out of stock — the "
            + "order becomes PAYMENT_FAILED / OUT_OF_STOCK (ORD_015)"),
            @ApiResponse(responseCode = "404", description = MINE_404),
            @ApiResponse(responseCode = "503", description = "inventory unavailable (ORD_007), the order stays PENDING_PAYMENT")})
    @PostMapping("/api/v1/shop/{slug}/orders/{orderNo}/pay")
    public ApiMessageDto<OrderDto> pay(@PathVariable String slug, @PathVariable String orderNo,
                                       @AuthenticationPrincipal CustomerPrincipal me, @Valid @RequestBody PayForm form) {
        return ApiMessageDto.ok(service.pay(slug, me.sub(), orderNo, form.outcome()));
    }

    @Operation(summary = "Cancel before payment", description = "PENDING_PAYMENT / PAYMENT_FAILED → CANCELLED; a paid order "
            + "cannot be cancelled here.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Paid order (ORD_017)"),
            @ApiResponse(responseCode = "404", description = MINE_404)})
    @PostMapping("/api/v1/shop/{slug}/orders/{orderNo}/cancel")
    public ApiMessageDto<OrderDto> cancel(@PathVariable String slug, @PathVariable String orderNo,
                                          @AuthenticationPrincipal CustomerPrincipal me) {
        return ApiMessageDto.ok(service.cancel(slug, me.sub(), orderNo));
    }
}
