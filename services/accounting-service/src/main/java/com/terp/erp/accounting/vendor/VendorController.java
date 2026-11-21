package com.terp.erp.accounting.vendor;

import com.terp.erp.accounting.vendor.VendorService.VendorDto;
import com.terp.erp.accounting.vendor.VendorService.VendorForm;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
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

@Tag(name = "Vendors")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounting/vendors")
@SecurityRequirement(name = "bearerAuth")
public class VendorController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String NOT_FOUND = "No such vendor in your company (ACC_004)";
    // TODO(assumption): purchase users may need read access too once purchase invoices exist.
    private static final String READ = "@PermissionChecker.has('VENDOR_VIEW')";

    private final VendorService service;

    @Operation(summary = "Vendors of the company",
            description = "keyword = vendor code, company name, contact person, email or tax number.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
    @PreAuthorize(READ)
    @GetMapping
    public ApiMessageDto<ResponseListDto<VendorDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "vendorCode", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, pageable)));
    }

    @Operation(summary = "Vendor details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(READ)
    @GetMapping("/{id}")
    public ApiMessageDto<VendorDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a vendor", description = "The vendor code (VEN-0001, ...) is generated per "
            + "company. shippingSameAsBilling = true copies the billing address into shipping.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "User account already linked to another "
            + "vendor (ACC_005), super admin without companyId (ACC_001), invalid field (COMMON)"))
    @PreAuthorize("@PermissionChecker.has('VENDOR_MANAGE')")
    @PostMapping
    public ApiMessageDto<VendorDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody VendorForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a vendor", description = "Full replacement: omitted optional fields are cleared. "
            + "The vendor code never changes.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "User account already linked to another vendor "
                    + "(ACC_005), invalid field (COMMON)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('VENDOR_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<VendorDto> update(@PathVariable Long id, @Valid @RequestBody VendorForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a vendor", description = "Soft delete; its code is never reused.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('VENDOR_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
