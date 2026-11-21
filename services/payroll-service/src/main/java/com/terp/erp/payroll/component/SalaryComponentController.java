package com.terp.erp.payroll.component;

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
import com.terp.erp.payroll.component.SalaryComponentService.ComponentDto;
import com.terp.erp.payroll.component.SalaryComponentService.ComponentForm;
import com.terp.erp.payroll.component.SalaryComponentService.StatusForm;

import java.util.List;

import static com.terp.erp.payroll.common.Texts.COMPANY;

@Tag(name = "Salary components")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payroll/components")
@SecurityRequirement(name = "bearerAuth")
public class SalaryComponentController {
    private static final String NOT_FOUND = "No such component in your company (PAY_002)";

    private final SalaryComponentService service;

    @Operation(summary = "Salary components of the company", description = "keyword matches the name or "
            + "description; type / calculation / status = the All Types / All Calculations / All Statuses filters.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ComponentDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) SalaryComponent.EType type,
            @RequestParam(required = false) SalaryComponent.ECalculation calculation,
            @RequestParam(required = false) SalaryComponent.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, type, calculation, status,
                pageable)));
    }

    @Operation(summary = "Active components for the salary structure form (earnings first)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<ComponentDto>> options(@Parameter(description = COMPANY)
                                                     @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Component details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ComponentDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a component")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (PAY_003), percentage over 100 "
            + "(PAY_004), super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PostMapping
    public ApiMessageDto<ComponentDto> create(@Parameter(description = COMPANY) @RequestParam(required = false)
                                              Long companyId, @Valid @RequestBody ComponentForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a component", description = "Full replacement; status omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "PAY_003, PAY_004"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ComponentDto> update(@PathVariable Long id, @Valid @RequestBody ComponentForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Activate / deactivate a component")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<ComponentDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a component")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
