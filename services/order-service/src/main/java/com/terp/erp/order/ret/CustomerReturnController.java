package com.terp.erp.order.ret;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.order.ret.ReturnDtos.RequestForm;
import com.terp.erp.order.ret.ReturnDtos.ReturnDto;
import com.terp.erp.security.CustomerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The signed-in customer's returns of their own delivered orders (customer tokens only). */
@Tag(name = "Returns (customer)")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CustomerReturnController {
    private final ReturnService service;

    @Operation(summary = "Ask to return delivered items", description = "Per item and quantity, within 30 days of "
            + "delivery (Asia/Seoul). The refund is what was paid for those units (coupon and points spread pro rata) minus "
            + "the pick-up fee for a change of mind; delivery and bag fees are not refunded.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not delivered (RET_001), period over (RET_002), item "
            + "not on the order or quantity too high (RET_003), nothing left after the fee (RET_004), a return already in "
            + "progress (RET_005), invalid field (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = "Not a member (ORD_001), no such order of yours (ORD_018)"),
            @ApiResponse(responseCode = "503", description = "member-service unavailable (ORD_007)")})
    @PostMapping("/api/v1/shop/{slug}/orders/{orderNo}/returns")
    public ApiMessageDto<ReturnDto> request(@PathVariable String slug, @PathVariable String orderNo,
                                            @AuthenticationPrincipal CustomerPrincipal me, @Valid @RequestBody RequestForm form) {
        return ApiMessageDto.ok(service.request(slug, me.sub(), orderNo, form));
    }

    @Operation(summary = "My returns of this order", description = "Newest first.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Not a member (ORD_001), no such order of yours (ORD_018)"))
    @GetMapping("/api/v1/shop/{slug}/orders/{orderNo}/returns")
    public ApiMessageDto<List<ReturnDto>> mine(@PathVariable String slug, @PathVariable String orderNo,
                                               @AuthenticationPrincipal CustomerPrincipal me) {
        return ApiMessageDto.ok(service.mine(slug, me.sub(), orderNo));
    }
}
