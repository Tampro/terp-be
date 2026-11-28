package com.terp.erp.order.mileage;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.order.mileage.MileageDtos.AccountDto;
import com.terp.erp.order.mileage.MileageDtos.AdjustForm;
import com.terp.erp.order.mileage.MileageDtos.PolicyDto;
import com.terp.erp.order.mileage.MileageDtos.PolicyForm;
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
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Mileage (Back Office)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order")
@SecurityRequirement(name = "bearerAuth")
public class MileageController {
    private static final String COMPANY = "Super admin only: the company to act on";
    private final MileageService service;

    @Operation(summary = "The company's mileage policy", description = "saved = false: the defaults (on, 1 %, 12 months, "
            + "1,000 minimum).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (MIL_002)"))
    @PreAuthorize("@PermissionChecker.has('MILEAGE_VIEW')")
    @GetMapping("/mileage-policy")
    public ApiMessageDto<PolicyDto> policy(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.policy(companyId));
    }

    @Operation(summary = "Replace the mileage policy", description = "PUT is full replacement. Off: no earning, no spending.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "minRedeem not a multiple of 10 (MIL_001), super admin "
            + "without companyId (MIL_002), invalid field (COMMON_001)"))
    @PreAuthorize("@PermissionChecker.has('MILEAGE_MANAGE')")
    @PutMapping("/mileage-policy")
    public ApiMessageDto<PolicyDto> savePolicy(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                               @Valid @RequestBody PolicyForm form) {
        return ApiMessageDto.ok(service.savePolicy(companyId, form));
    }

    @Operation(summary = "A customer's points and statement", description = "Newest entries first.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Super admin without companyId (MIL_002)"),
            @ApiResponse(responseCode = "404", description = "No mileage of this customer in your company (MIL_007)")})
    @PreAuthorize("@PermissionChecker.has('MILEAGE_VIEW')")
    @GetMapping("/mileage/customers/{customerId}")
    public ApiMessageDto<AccountDto> account(@PathVariable long customerId,
                                             @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                             @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiMessageDto.ok(service.account(companyId, customerId, pageable));
    }

    @Operation(summary = "Add or take points", description = "+ adds a lot with the policy's expiry; − takes from the "
            + "soonest-expiring lots.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "0 points (MIL_001), more than the balance (MIL_005), "
            + "invalid field (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = "No mileage of this customer in your company (MIL_007)")})
    @PreAuthorize("@PermissionChecker.has('MILEAGE_MANAGE')")
    @PostMapping("/mileage/customers/{customerId}/adjust")
    public ApiMessageDto<AccountDto> adjust(@PathVariable long customerId,
                                            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                            @Valid @RequestBody AdjustForm form,
                                            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiMessageDto.ok(service.adjust(companyId, customerId, form.points(), form.reason(), pageable));
    }
}
