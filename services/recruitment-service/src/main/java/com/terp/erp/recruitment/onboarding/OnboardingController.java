package com.terp.erp.recruitment.onboarding;

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
import com.terp.erp.recruitment.onboarding.OnboardingService.OnboardingDetail;
import com.terp.erp.recruitment.onboarding.OnboardingService.OnboardingForm;
import com.terp.erp.recruitment.onboarding.OnboardingService.OnboardingRow;
import com.terp.erp.recruitment.onboarding.OnboardingService.StatusCounts;
import com.terp.erp.recruitment.onboarding.OnboardingService.StatusForm;

@Tag(name = "Candidate Onboarding")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/onboardings")
@SecurityRequirement(name = "bearerAuth")
public class OnboardingController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String KEYWORD = "Matches the employee's name or email";

    private final OnboardingService service;

    @Operation(summary = "Onboardings of the company", description = "employeeId = Employee filter, status = the tab.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<OnboardingRow>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) CandidateOnboarding.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, employeeId, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Pending, In Progress, Completed)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, employeeId));
    }

    @Operation(summary = "Onboarding details", description = "Employee information and the checklist's active items "
            + "with their due dates.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such onboarding in your company (REC_040)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<OnboardingDetail> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Start an onboarding (status PENDING)",
            description = "The employee and buddy are employee-service employees (needs EMPLOYEE_VIEW there). With "
                    + "candidateId, that hired candidate is linked to the employee.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Buddy is the employee (REC_042), employee already "
                    + "onboarded (REC_043), candidate not hired (REC_044) or linked elsewhere (REC_045), inactive "
                    + "checklist (REC_014)"),
            @ApiResponse(responseCode = "404", description = "No such checklist (REC_038) or candidate (REC_015); "
                    + "employee (EMP_001) relayed"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (REC_012)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<OnboardingDetail> create(@Valid @RequestBody OnboardingForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit an onboarding", description = "Full replacement; the status is kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "REC_014, REC_042, REC_043, REC_044, REC_045"),
            @ApiResponse(responseCode = "404", description = "REC_040, REC_038, REC_015, EMP_001"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (REC_012)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<OnboardingDetail> update(@PathVariable Long id, @Valid @RequestBody OnboardingForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Update Status (Pending, In Progress, Completed)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such onboarding in your company (REC_040)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<OnboardingDetail> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete an onboarding", description = "The linked candidate is released.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such onboarding in your company (REC_040)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
