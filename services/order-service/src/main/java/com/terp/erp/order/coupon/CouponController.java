package com.terp.erp.order.coupon;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.order.coupon.CouponDtos.CouponDto;
import com.terp.erp.order.coupon.CouponDtos.CouponForm;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Coupons (Back Office)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order/coupons")
@SecurityRequirement(name = "bearerAuth")
public class CouponController {
    private static final String NOT_FOUND = "No such coupon in your company (CPN_005)";
    private static final String INVALID = "Duplicate code (CPN_001), percent over 100 or endsAt not after startsAt "
            + "(CPN_002), invalid field (COMMON_001)";

    private final CouponService service;

    @Operation(summary = "Coupons of the company", description = "Not deleted ones; usedCount = uses held by orders.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (CPN_006)"))
    @PreAuthorize("@PermissionChecker.has('COUPON_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<CouponDto>> list(
            @Parameter(description = "Super admin only: the company to act on") @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Part of the code or name") @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(companyId, active, q, pageable)));
    }

    @Operation(summary = "A coupon")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('COUPON_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<CouponDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Create a coupon")
    @ApiResponses(@ApiResponse(responseCode = "400", description = INVALID + ", super admin without companyId (CPN_006)"))
    @PreAuthorize("@PermissionChecker.has('COUPON_MANAGE')")
    @PostMapping
    public ApiMessageDto<CouponDto> create(
            @Parameter(description = "Super admin only: the company to act on") @RequestParam(required = false) Long companyId,
            @Valid @RequestBody CouponForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Replace a coupon", description = "PUT is full replacement. The code of a coupon already used "
            + "cannot change.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = INVALID + ", code of a used coupon (CPN_003)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('COUPON_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<CouponDto> update(@PathVariable Long id, @Valid @RequestBody CouponForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete an unused coupon", description = "Soft delete; a used coupon is refused — switch it off.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Already used (CPN_004)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('COUPON_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
