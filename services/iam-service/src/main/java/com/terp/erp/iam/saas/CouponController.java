package com.terp.erp.iam.saas;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.format.annotation.DateTimeFormat;
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
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.iam.saas.CouponDtos.CouponCountsDto;
import com.terp.erp.iam.saas.CouponDtos.CouponDetailDto;
import com.terp.erp.iam.saas.CouponDtos.CouponDto;
import com.terp.erp.iam.saas.CouponDtos.CouponForm;
import com.terp.erp.iam.saas.CouponDtos.CouponStatusForm;

import java.time.LocalDate;

@Tag(name = "SaaS — coupons (super admin)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/saas/admin/coupons")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@PermissionChecker.isSuperAdmin()")
public class CouponController {
    private final CouponService coupons;

    @Operation(summary = "Coupons list", description = "keyword matches name or code; type PERCENTAGE | FLAT; "
            + "dateFrom/dateTo are inclusive UTC creation days; active true = tab Active, false = Inactive, omitted = All. "
            + "timesUsed counts pending + approved orders.")
    @GetMapping
    public ApiMessageDto<ResponseListDto<CouponDto>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ECouponType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(coupons.search(keyword, type, dateFrom, dateTo, active, pageable)));
    }

    @Operation(summary = "Counts for the status tabs (All / Active / Inactive), same filters as the list")
    @GetMapping("/status-counts")
    public ApiMessageDto<CouponCountsDto> counts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ECouponType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiMessageDto.ok(coupons.counts(keyword, type, dateFrom, dateTo));
    }

    @Operation(summary = "View Details: coupon and usage history")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such coupon (SAAS_004)"))
    @GetMapping("/{id}")
    public ApiMessageDto<CouponDetailDto> detail(@PathVariable Long id) {
        return ApiMessageDto.ok(coupons.detail(id));
    }

    @Operation(summary = "Add a coupon", description = "codeGeneration AUTO generates an 8-character code; MANUAL "
            + "(default) needs `code`.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Code taken (SAAS_005), percentage > 100 (SAAS_012), "
            + "min > max spend (SAAS_020), manual code missing (SAAS_021)"))
    @PostMapping
    public ApiMessageDto<CouponDto> create(@Valid @RequestBody CouponForm form) {
        return ApiMessageDto.ok(coupons.create(form));
    }

    @Operation(summary = "Edit a coupon (full replacement)", description = "PUT replaces every field: omitted limits "
            + "become unlimited. codeGeneration AUTO keeps the current code.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Code taken (SAAS_005), percentage > 100 (SAAS_012), "
                    + "min > max spend (SAAS_020), manual code missing (SAAS_021)"),
            @ApiResponse(responseCode = "404", description = "No such coupon (SAAS_004)")})
    @PutMapping("/{id}")
    public ApiMessageDto<CouponDto> update(@PathVariable Long id, @Valid @RequestBody CouponForm form) {
        return ApiMessageDto.ok(coupons.update(id, form));
    }

    @Operation(summary = "Active / Inactive toggle")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such coupon (SAAS_004)"))
    @PutMapping("/{id}/status")
    public ApiMessageDto<CouponDto> status(@PathVariable Long id, @Valid @RequestBody CouponStatusForm form) {
        return ApiMessageDto.ok(coupons.setEnabled(id, form.enabled()));
    }

    @Operation(summary = "Delete a coupon", description = "Soft delete: orders that used it keep their discount; "
            + "the code can be reused.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such coupon (SAAS_004)"))
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        coupons.delete(id);
        return ApiMessageDto.ok(null);
    }
}
