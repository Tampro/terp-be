package com.terp.erp.recruitment.assessment;

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
import com.terp.erp.recruitment.assessment.AssessmentService.AssessmentDto;
import com.terp.erp.recruitment.assessment.AssessmentService.AssessmentForm;
import com.terp.erp.recruitment.assessment.AssessmentService.StatusCounts;

@Tag(name = "Candidate Assessments")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/assessments")
@SecurityRequirement(name = "bearerAuth")
public class AssessmentController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String KEYWORD = "Matches the assessment name or the candidate's name / email";

    private final AssessmentService service;

    @Operation(summary = "Assessments of the company", description = "candidateId = Candidate filter, status = the "
            + "tab. Sort by assessmentDate, score, createdDate.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<AssessmentDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long candidateId,
            @RequestParam(required = false) CandidateAssessment.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "assessmentDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, candidateId, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Pending, Pass, Fail)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long candidateId) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, candidateId));
    }

    @Operation(summary = "Assessment details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such assessment in your company (REC_041)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<AssessmentDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add an assessment", description = "Conducted By is an employee (needs EMPLOYEE_VIEW in "
            + "employee-service). Only for a candidate with an interview feedback recommending HIRE or STRONG_HIRE.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Score above max score (REC_046), candidate not "
                    + "recommended Hire / Strong Hire (REC_057)"),
            @ApiResponse(responseCode = "404", description = "No such candidate (REC_015); employee (EMP_001) relayed"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (REC_012)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<AssessmentDto> create(@Valid @RequestBody AssessmentForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit an assessment", description = "Full replacement; status omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Score above max score (REC_046)"),
            @ApiResponse(responseCode = "404", description = "REC_041, REC_015, EMP_001"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (REC_012)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<AssessmentDto> update(@PathVariable Long id, @Valid @RequestBody AssessmentForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete an assessment")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such assessment in your company (REC_041)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
