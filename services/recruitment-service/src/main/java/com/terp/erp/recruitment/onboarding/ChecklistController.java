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
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.onboarding.ChecklistService.ChecklistDto;
import com.terp.erp.recruitment.onboarding.ChecklistService.ChecklistForm;
import com.terp.erp.recruitment.onboarding.ChecklistService.Ref;
import com.terp.erp.recruitment.onboarding.ChecklistService.StatusCounts;
import com.terp.erp.recruitment.onboarding.ChecklistService.StatusForm;

import java.util.List;

@Tag(name = "Onboarding Checklists")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/onboarding-checklists")
@SecurityRequirement(name = "bearerAuth")
public class ChecklistController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final ChecklistService service;

    @Operation(summary = "Onboarding checklists of the company", description = "items = number of tasks.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ChecklistDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword));
    }

    @Operation(summary = "Active checklists for dropdowns (Start Onboarding, Add Item)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Ref>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Checklist details", description = "Its tasks: GET /checklist-items?checklistId=")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such checklist in your company (REC_038)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ChecklistDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a checklist", description = "Set as Default clears the previous default.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (REC_004), super admin without "
            + "companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<ChecklistDto> create(@Parameter(description = COMPANY) @RequestParam(required = false)
                                              Long companyId, @Valid @RequestBody ChecklistForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a checklist", description = "Full replacement; status omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (REC_004)"),
            @ApiResponse(responseCode = "404", description = "No such checklist in your company (REC_038)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ChecklistDto> update(@PathVariable Long id, @Valid @RequestBody ChecklistForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Toggle Status (Active / Inactive)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such checklist in your company (REC_038)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<ChecklistDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a checklist and its items")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Used by an onboarding (REC_013)"),
            @ApiResponse(responseCode = "404", description = "No such checklist in your company (REC_038)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
