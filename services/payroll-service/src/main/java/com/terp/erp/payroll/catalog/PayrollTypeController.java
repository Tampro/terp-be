package com.terp.erp.payroll.catalog;

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
import com.terp.erp.payroll.catalog.PayrollType.EKind;
import com.terp.erp.payroll.catalog.PayrollTypeService.TypeDto;
import com.terp.erp.payroll.catalog.PayrollTypeService.TypeForm;

import static com.terp.erp.payroll.common.Texts.COMPANY;

@Tag(name = "Payroll types (loan, allowance, deduction)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payroll/types")
@SecurityRequirement(name = "bearerAuth")
public class PayrollTypeController {
    private static final String NOT_FOUND = "No such type in your company (PAY_027)";

    private final PayrollTypeService service;

    @Operation(summary = "Types of one kind", description = "kind = LOAN, ALLOWANCE or DEDUCTION; keyword matches "
            + "name or code; active = the status filter.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TypeDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam EKind kind,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, kind, active, keyword, pageable)));
    }

    @Operation(summary = "Type details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<TypeDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a type")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Code taken (PAY_028), super admin without "
            + "companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PostMapping
    public ApiMessageDto<TypeDto> create(@Parameter(description = COMPANY) @RequestParam(required = false)
                                         Long companyId, @Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a type", description = "Full replacement; active omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Code taken (PAY_028), kind of a loan type in use "
                    + "changed (PAY_005)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<TypeDto> update(@PathVariable Long id, @Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a type")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "A loan type used by a loan (PAY_005)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
