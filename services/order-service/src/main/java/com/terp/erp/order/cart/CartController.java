package com.terp.erp.order.cart;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.order.cart.CartService.BagForm;
import com.terp.erp.order.cart.CartService.CartDto;
import com.terp.erp.order.cart.CartService.KeepForm;
import com.terp.erp.order.cart.CartService.QuantityForm;
import com.terp.erp.security.CustomerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in customer's cart at one store of a shop (customer tokens only). Every answer is the whole cart, priced live. */
@Tag(name = "Cart (customer)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shop/{slug}/stores/{storeId}/cart")
@SecurityRequirement(name = "bearerAuth")
public class CartController {
    private static final String CTX = "Not a member of the shop (ORD_001), store not of the shop or inactive (ORD_005)";
    private static final String DOWN = "member / store / inventory unavailable (ORD_007)";

    private final CartService service;

    @Operation(summary = "My cart at this store", description = "Prices, limits and sold-out are read live; a line no longer "
            + "on sale is available=false and left out of the totals. Delivery is free from freeDeliveryThreshold; bagFee "
            + "applies when bagRequested; minimumReached compares the subtotal (after a coupon) with minOrderAmount.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = CTX), @ApiResponse(responseCode = "503", description = DOWN)})
    @GetMapping
    public ApiMessageDto<CartDto> view(@PathVariable String slug, @PathVariable long storeId,
                                       @AuthenticationPrincipal CustomerPrincipal me,
                                       @io.swagger.v3.oas.annotations.Parameter(description = "Preview a coupon: discount "
                                               + "and totals after it, or couponError CPN_007-CPN_011")
                                       @org.springframework.web.bind.annotation.RequestParam(required = false) String couponCode,
                                       @io.swagger.v3.oas.annotations.Parameter(description = "Preview points to spend: "
                                               + "pointsUsed and totals after them, or pointsError MIL_003-MIL_006")
                                       @org.springframework.web.bind.annotation.RequestParam(required = false) Integer usePoints) {
        return ApiMessageDto.ok(service.view(slug, me.sub(), storeId, couponCode, usePoints));
    }

    @Operation(summary = "Put an item with this quantity", description = "Creates the line or replaces its quantity (1-99, "
            + "at most the item's purchase limit). At most 100 lines.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Over the purchase limit (ORD_002), 101st line (ORD_004), "
            + "quantity out of 1-99 (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = CTX + ", item not on sale at this store (ORD_003)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PutMapping("/items/{itemId}")
    public ApiMessageDto<CartDto> put(@PathVariable String slug, @PathVariable long storeId, @PathVariable long itemId,
                                      @AuthenticationPrincipal CustomerPrincipal me, @Valid @RequestBody QuantityForm form) {
        return ApiMessageDto.ok(service.setQuantity(slug, me.sub(), storeId, itemId, form.quantity()));
    }

    @Operation(summary = "Remove an item")
    @ApiResponses({@ApiResponse(responseCode = "404", description = CTX + ", not in the cart (ORD_006)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @DeleteMapping("/items/{itemId}")
    public ApiMessageDto<CartDto> remove(@PathVariable String slug, @PathVariable long storeId, @PathVariable long itemId,
                                         @AuthenticationPrincipal CustomerPrincipal me) {
        return ApiMessageDto.ok(service.remove(slug, me.sub(), storeId, itemId));
    }

    @Operation(summary = "Keep an item past 90 days", description = "keep=true exempts the line from the 90-day clean-up.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = CTX + ", not in the cart (ORD_006)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PutMapping("/items/{itemId}/keep")
    public ApiMessageDto<CartDto> keep(@PathVariable String slug, @PathVariable long storeId, @PathVariable long itemId,
                                       @AuthenticationPrincipal CustomerPrincipal me, @Valid @RequestBody KeepForm form) {
        return ApiMessageDto.ok(service.keep(slug, me.sub(), storeId, itemId, form.keep()));
    }

    @Operation(summary = "Ask for the paid bag (or not)")
    @ApiResponses({@ApiResponse(responseCode = "404", description = CTX), @ApiResponse(responseCode = "503", description = DOWN)})
    @PutMapping("/bag")
    public ApiMessageDto<CartDto> bag(@PathVariable String slug, @PathVariable long storeId,
                                      @AuthenticationPrincipal CustomerPrincipal me, @Valid @RequestBody BagForm form) {
        return ApiMessageDto.ok(service.bag(slug, me.sub(), storeId, form.bagRequested()));
    }

    @Operation(summary = "Empty the cart")
    @ApiResponses({@ApiResponse(responseCode = "404", description = CTX), @ApiResponse(responseCode = "503", description = DOWN)})
    @DeleteMapping
    public ApiMessageDto<CartDto> clear(@PathVariable String slug, @PathVariable long storeId,
                                        @AuthenticationPrincipal CustomerPrincipal me) {
        return ApiMessageDto.ok(service.clear(slug, me.sub(), storeId));
    }
}
