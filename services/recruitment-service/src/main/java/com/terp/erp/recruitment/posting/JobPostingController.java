package com.terp.erp.recruitment.posting;

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
import com.terp.erp.recruitment.posting.JobPostingDtos.PostingDto;
import com.terp.erp.recruitment.posting.JobPostingDtos.PostingForm;
import com.terp.erp.recruitment.posting.JobPostingDtos.PostingOption;

import java.util.List;

@Tag(name = "Job Postings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/job-postings")
@SecurityRequirement(name = "bearerAuth")
public class JobPostingController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String RULES = "Min above max experience/salary (REC_008), custom application without URL "
            + "(REC_009), department of another branch (REC_010), inactive job type/location (REC_014)";
    private static final String NOT_FOUND = "No such job type / item (REC_001), location (REC_002), question (REC_003), "
            + "branch (EMP_002) or department (EMP_003)";

    private final JobPostingService service;

    @Operation(summary = "Job postings of the company",
            description = "keyword matches title or job code; status (DRAFT / PUBLISHED / CLOSED) and published filter. "
                    + "Sort by createdDate, title, applicationDeadline.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<PostingDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) JobPosting.EStatus status,
            @RequestParam(required = false) Boolean published,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, status, published, pageable)));
    }

    @Operation(summary = "Every job posting (code and title), for dropdowns")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<PostingOption>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Job posting details", description = "With rich text, branch / department names and questions.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such posting in your company (REC_006)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<PostingDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Create a job posting (DRAFT)",
            description = "The job code is generated (JOB-0001...). Rich text is sanitized. Needs ORG_VIEW too when a "
                    + "branch or department is given (checked in employee-service as the caller).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = RULES + ", no company (REC_005)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<PostingDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                            @Valid @RequestBody PostingForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a job posting (full replacement)",
            description = "Every form field is replaced (omitted = cleared / default); code and published state kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = RULES),
            @ApiResponse(responseCode = "404", description = "No such posting (REC_006); " + NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<PostingDto> update(@PathVariable Long id, @Valid @RequestBody PostingForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Publish a job posting (status PUBLISHED)",
            description = "From DRAFT, or re-opens a CLOSED posting.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such posting in your company (REC_006)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/publish")
    public ApiMessageDto<PostingDto> publish(@PathVariable Long id) {
        return ApiMessageDto.ok(service.setPublished(id, true));
    }

    @Operation(summary = "Unpublish a job posting (back to DRAFT)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "A closed posting cannot go back to draft (REC_055)"),
            @ApiResponse(responseCode = "404", description = "No such posting in your company (REC_006)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/unpublish")
    public ApiMessageDto<PostingDto> unpublish(@PathVariable Long id) {
        return ApiMessageDto.ok(service.setPublished(id, false));
    }

    @Operation(summary = "Close a published job posting (status CLOSED)",
            description = "Hidden from the career page, applications refused (REC_054). Publish re-opens it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Only a published posting can be closed (REC_056)"),
            @ApiResponse(responseCode = "404", description = "No such posting in your company (REC_006)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/close")
    public ApiMessageDto<PostingDto> close(@PathVariable Long id) {
        return ApiMessageDto.ok(service.close(id));
    }

    @Operation(summary = "Delete a job posting")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "It still has candidates (REC_013)"),
            @ApiResponse(responseCode = "404", description = "No such posting in your company (REC_006)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
