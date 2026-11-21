package com.terp.erp.recruitment.catalog;

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
import com.terp.erp.recruitment.catalog.CatalogDtos.CatalogDto;
import com.terp.erp.recruitment.catalog.CatalogDtos.CatalogForm;
import com.terp.erp.recruitment.catalog.CatalogDtos.Option;
import com.terp.erp.recruitment.catalog.CatalogDtos.StatusForm;
import com.terp.erp.recruitment.common.EStatus;

import java.util.List;

/**
 * One controller for the four name / description / status catalogues:
 * /api/v1/recruitment/{job-categories | job-types | candidate-sources | interview-types}.
 */
@Tag(name = "Recruitment Catalogues")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/{kind:job-categories|job-types|candidate-sources|interview-types}")
@SecurityRequirement(name = "bearerAuth")
public class CatalogController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String KIND = "job-categories, job-types, candidate-sources or interview-types";

    private final CatalogService service;

    @Operation(summary = "Items of a catalogue",
            description = "keyword matches name or description; status = ACTIVE / INACTIVE (omitted = all). "
                    + "Sort by name, createdDate.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<CatalogDto>> list(
            @Parameter(description = KIND) @PathVariable String kind,
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(ECatalogKind.ofPath(kind), companyId, keyword, status, pageable)));
    }

    @Operation(summary = "Active items for dropdowns")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Option>> options(
            @Parameter(description = KIND) @PathVariable String kind,
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(ECatalogKind.ofPath(kind), companyId));
    }

    @Operation(summary = "Item details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such item in your company (REC_001)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<CatalogDto> get(@Parameter(description = KIND) @PathVariable String kind,
                                         @PathVariable Long id) {
        return ApiMessageDto.ok(service.get(ECatalogKind.ofPath(kind), id));
    }

    @Operation(summary = "Add an item", description = "status omitted = ACTIVE.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (REC_004), no company (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<CatalogDto> create(@Parameter(description = KIND) @PathVariable String kind,
                                            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                            @Valid @RequestBody CatalogForm form) {
        return ApiMessageDto.ok(service.create(ECatalogKind.ofPath(kind), companyId, form));
    }

    @Operation(summary = "Edit an item (full replacement)",
            description = "Omitted description is cleared; omitted status is kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (REC_004)"),
            @ApiResponse(responseCode = "404", description = "No such item in your company (REC_001)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<CatalogDto> update(@Parameter(description = KIND) @PathVariable String kind,
                                            @PathVariable Long id, @Valid @RequestBody CatalogForm form) {
        return ApiMessageDto.ok(service.update(ECatalogKind.ofPath(kind), id, form));
    }

    @Operation(summary = "Activate or deactivate an item")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such item in your company (REC_001)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<CatalogDto> status(@Parameter(description = KIND) @PathVariable String kind,
                                            @PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(ECatalogKind.ofPath(kind), id, form.status()));
    }

    @Operation(summary = "Delete an item")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Job type used by a posting, source used by a "
                    + "candidate or interview type used by an interview (REC_013)"),
            @ApiResponse(responseCode = "404", description = "No such item in your company (REC_001)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@Parameter(description = KIND) @PathVariable String kind, @PathVariable Long id) {
        service.delete(ECatalogKind.ofPath(kind), id);
        return ApiMessageDto.ok(null);
    }
}
