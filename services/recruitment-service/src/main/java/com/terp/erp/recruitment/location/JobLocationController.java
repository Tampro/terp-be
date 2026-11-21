package com.terp.erp.recruitment.location;

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
import com.terp.erp.recruitment.catalog.CatalogDtos.Option;
import com.terp.erp.recruitment.catalog.CatalogDtos.StatusCounts;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.location.JobLocationService.LocationDto;
import com.terp.erp.recruitment.location.JobLocationService.LocationForm;
import com.terp.erp.recruitment.location.JobLocationService.StatusForm;

import java.util.List;

@Tag(name = "Job Locations")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/job-locations")
@SecurityRequirement(name = "bearerAuth")
public class JobLocationController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String TYPE = "Type filter: true = Remote, false = On-site, omitted = both";

    private final JobLocationService service;

    @Operation(summary = "Job locations of the company",
            description = "keyword matches name, address, city or country. Sort by name, createdDate.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<LocationDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @Parameter(description = TYPE) @RequestParam(required = false) Boolean remote,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, remote, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)", description = "Same keyword and Type filters.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @Parameter(description = TYPE) @RequestParam(required = false) Boolean remote) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, remote));
    }

    @Operation(summary = "Active locations for dropdowns")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Option>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Job location details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such location in your company (REC_002)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<LocationDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a job location", description = "status omitted = ACTIVE.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (REC_004), no company (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<LocationDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                             @Valid @RequestBody LocationForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a job location (full replacement)",
            description = "Omitted address fields are cleared; omitted status is kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (REC_004)"),
            @ApiResponse(responseCode = "404", description = "No such location in your company (REC_002)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<LocationDto> update(@PathVariable Long id, @Valid @RequestBody LocationForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Activate or deactivate a job location")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such location in your company (REC_002)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<LocationDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a job location")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such location in your company (REC_002)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
