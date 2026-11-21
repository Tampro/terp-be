package com.terp.erp.employee.lifecycle;

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
import com.terp.erp.employee.lifecycle.PromotionService.PromotionDto;
import com.terp.erp.employee.lifecycle.PromotionService.PromotionForm;
import com.terp.erp.employee.lifecycle.PromotionService.StatusCounts;
import com.terp.erp.employee.lifecycle.PromotionService.StatusForm;

import java.time.LocalDate;

import static com.terp.erp.employee.lifecycle.AwardController.COMPANY;
import static com.terp.erp.employee.lifecycle.AwardController.KEYWORD;

@Tag(name = "Promotions")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle/promotions")
@SecurityRequirement(name = "bearerAuth")
public class PromotionController {
    private final PromotionService service;

    @Operation(summary = "Promotions of the company", description = "designationId = the new designation; Date From / "
            + "To on the promotion date; status = the tab. keyword also matches the reason.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<PromotionDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long designationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) EDecision status,
            @ParameterObject @PageableDefault(size = 20, sort = "promotionDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, designationId, from, to, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Pending, Approved, Rejected)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long designationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, designationId, from, to));
    }

    @Operation(summary = "Promotion details", description = "With a signed link to the document.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such promotion in your company (EMP_044)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<PromotionDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a promotion", description = "The new designation must be another one of the employee's "
            + "department. status APPROVED changes the employee's designation at once.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Designation of another department or the current one "
                    + "(EMP_047), unknown (EMP_004) or inactive (EMP_031), effective before promotion date "
                    + "(EMP_049), file (EMP_012)"),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PostMapping
    public ApiMessageDto<PromotionDto> create(@Valid @RequestBody PromotionForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a pending promotion", description = "Full replacement; status is ignored here.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (EMP_046), EMP_004, EMP_031, EMP_047, "
                    + "EMP_049, EMP_012"),
            @ApiResponse(responseCode = "404", description = "No such promotion (EMP_044) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<PromotionDto> update(@PathVariable Long id, @Valid @RequestBody PromotionForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Update Status (Pending, Approved, Rejected)",
            description = "Only from Pending. APPROVED changes the employee's designation now.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (EMP_046), employee moved since "
                    + "(EMP_047)"),
            @ApiResponse(responseCode = "404", description = "No such promotion in your company (EMP_044)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<PromotionDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a pending promotion")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (EMP_046)"),
            @ApiResponse(responseCode = "404", description = "No such promotion in your company (EMP_044)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
