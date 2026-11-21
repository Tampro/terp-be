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
import com.terp.erp.employee.lifecycle.AwardService.AwardDto;
import com.terp.erp.employee.lifecycle.AwardService.AwardForm;

import java.time.LocalDate;

@Tag(name = "Awards")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle/awards")
@SecurityRequirement(name = "bearerAuth")
public class AwardController {
    static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    static final String KEYWORD = "Matches the employee's name, email or employee no.";

    private final AwardService service;

    @Operation(summary = "Awards of the company", description = "Filters: Award Type, Employee, Date From / To (award "
            + "date). keyword also matches the gift.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<AwardDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long awardTypeId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20, sort = "awardDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, awardTypeId, employeeId, from, to, pageable)));
    }

    @Operation(summary = "Award details", description = "With signed links to the certificate and photo.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such award in your company (EMP_043)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<AwardDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add an award", description = "Upload the certificate and photo to the media library first.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Award type not usable (EMP_042), file not in the "
                    + "company's library (EMP_012)"),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PostMapping
    public ApiMessageDto<AwardDto> create(@Valid @RequestBody AwardForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit an award", description = "Full replacement.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "EMP_042, EMP_012"),
            @ApiResponse(responseCode = "404", description = "No such award (EMP_043) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<AwardDto> update(@PathVariable Long id, @Valid @RequestBody AwardForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete an award")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such award in your company (EMP_043)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
