package com.terp.erp.recruitment.round;

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
import com.terp.erp.recruitment.catalog.CatalogDtos.StatusCounts;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.round.InterviewRoundService.RoundDto;
import com.terp.erp.recruitment.round.InterviewRoundService.RoundForm;
import com.terp.erp.recruitment.round.InterviewRoundService.StatusForm;

import java.util.List;

@Tag(name = "Interview Rounds")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/interview-rounds")
@SecurityRequirement(name = "bearerAuth")
public class InterviewRoundController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final InterviewRoundService service;

    @Operation(summary = "Interview rounds of the company",
            description = "jobPostingId = the Job filter; keyword matches name or description. Sort by jobPostingId, "
                    + "sequenceNumber, createdDate.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<RoundDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = {"jobPostingId", "sequenceNumber"},
                    direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, jobPostingId, keyword, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)", description = "Same Job and keyword filters.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) String keyword) {
        return ApiMessageDto.ok(service.statusCounts(companyId, jobPostingId, keyword));
    }

    @Operation(summary = "Active rounds of a job posting, in sequence")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such posting in your company (REC_006)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<RoundDto>> ofPosting(@RequestParam Long jobPostingId) {
        return ApiMessageDto.ok(service.ofPosting(jobPostingId));
    }

    @Operation(summary = "Interview round details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such round in your company (REC_007)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<RoundDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add an interview round to a job posting", description = "status omitted = ACTIVE.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Sequence taken in that posting (REC_011)"),
            @ApiResponse(responseCode = "404", description = "No such posting in your company (REC_006)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<RoundDto> create(@Valid @RequestBody RoundForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit an interview round (full replacement)",
            description = "Omitted description is cleared; omitted status is kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Sequence taken in that posting (REC_011)"),
            @ApiResponse(responseCode = "404", description = "No such round (REC_007) or posting (REC_006)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<RoundDto> update(@PathVariable Long id, @Valid @RequestBody RoundForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Activate or deactivate an interview round")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such round in your company (REC_007)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<RoundDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete an interview round")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Used by an interview (REC_013)"),
            @ApiResponse(responseCode = "404", description = "No such round in your company (REC_007)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
