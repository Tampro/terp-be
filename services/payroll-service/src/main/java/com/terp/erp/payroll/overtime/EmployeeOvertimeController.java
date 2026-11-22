package com.terp.erp.payroll.overtime;

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
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.payroll.overtime.EmployeeOvertimeService.OvertimeDto;
import com.terp.erp.payroll.overtime.EmployeeOvertimeService.OvertimeForm;
import com.terp.erp.payroll.overtime.EmployeeOvertimeService.StatusForm;

import static com.terp.erp.payroll.common.Texts.COMPANY;

@Tag(name = "Employee overtime (manual)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payroll")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeOvertimeController {
    private static final String NOT_FOUND = "No such overtime in your company (PAY_033)";
    private static final String PAID = "Already paid by a payroll run (PAY_034)";
    private static final String DECIDED = "Already approved or rejected (PAY_043)";

    private final EmployeeOvertimeService service;

    @Operation(summary = "Manual overtime of an employee", description = "status = PENDING, APPROVED or REJECTED.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PAY_001)"))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/employees/{employeeId}/overtimes")
    public ApiMessageDto<ResponseListDto<OvertimeDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @PathVariable Long employeeId,
            @RequestParam(required = false) EmployeeOvertime.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(companyId, employeeId, status, pageable)));
    }

    @Operation(summary = "Add manual overtime", description = "amount = days × hours × rate. Only APPROVED overtime "
            + "is paid, in full, by the first processed run whose period overlaps [startDate, endDate]. Needs "
            + "EMPLOYEE_VIEW too.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "End before start (PAY_031), super admin without "
                    + "companyId (PAY_001)"),
            @ApiResponse(responseCode = "404", description = "Employee not in your company (PAY_033 / EMP_xxx)"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (PAY_006)")})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PostMapping("/employees/{employeeId}/overtimes")
    public ApiMessageDto<OvertimeDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @PathVariable Long employeeId, @Valid @RequestBody OvertimeForm form) {
        return ApiMessageDto.ok(service.create(companyId, employeeId, form));
    }

    @Operation(summary = "Overtime details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PAYROLL_VIEW')")
    @GetMapping("/overtimes/{id}")
    public ApiMessageDto<OvertimeDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Edit manual overtime", description = "Full replacement, only while PENDING; status omitted "
            + "= kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "End before start (PAY_031), " + PAID + ", " + DECIDED),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PutMapping("/overtimes/{id}")
    public ApiMessageDto<OvertimeDto> update(@PathVariable Long id, @Valid @RequestBody OvertimeForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Approve / reject overtime", description = "Allowed: PENDING -> APPROVED | REJECTED, once.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = PAID + ", " + DECIDED),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @PutMapping("/overtimes/{id}/status")
    public ApiMessageDto<OvertimeDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete manual overtime", description = "Only while PENDING.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = PAID + ", " + DECIDED),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PAYROLL_MANAGE')")
    @DeleteMapping("/overtimes/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
