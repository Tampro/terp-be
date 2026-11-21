package com.terp.erp.employee.training;

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
import com.terp.erp.employee.training.TrainingAssessmentService.AssessmentDto;
import com.terp.erp.employee.training.TrainingAssessmentService.AssessmentForm;
import com.terp.erp.employee.training.TrainingAssessmentService.ResultForm;
import com.terp.erp.employee.training.TrainingAssessmentService.TypeCounts;

import static com.terp.erp.employee.training.TrainingSupport.COMPANY;

@Tag(name = "Training assessments")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/training/assessments")
@SecurityRequirement(name = "bearerAuth")
public class TrainingAssessmentController {
    private static final String KEYWORD = "Matches the assessment or program name";
    private static final String NOT_FOUND = "No such assessment in your company (EMP_089)";
    private static final String RESULT_ERRORS = "Training not of the assessment's program (EMP_090), already has a "
            + "result (EMP_091)";

    private final TrainingAssessmentService service;

    @Operation(summary = "Training assessments of the company", description = "programId = the Training Program "
            + "filter; type = the tab; results = recorded results.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<AssessmentDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) TrainingAssessment.EType type,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, programId, type, pageable)));
    }

    @Operation(summary = "Counts of the type tabs (All, Quiz, Practical, Presentation)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/type-counts")
    public ApiMessageDto<TypeCounts> typeCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long programId) {
        return ApiMessageDto.ok(service.typeCounts(companyId, keyword, programId));
    }

    @Operation(summary = "Assessment details", description = "Information, statistics (total, pass rate, average "
            + "score, passed, failed) and the results; passed = score ≥ passing score.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<AssessmentDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add an assessment")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such program in your company (EMP_081)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PostMapping
    public ApiMessageDto<AssessmentDto> create(@Valid @RequestBody AssessmentForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit an assessment", description = "Full replacement; a new passing score re-grades the "
            + "results; the program cannot change once results exist.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Program change with results (EMP_010), program not in "
                    + "the company (EMP_081)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<AssessmentDto> update(@PathVariable Long id, @Valid @RequestBody AssessmentForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete an assessment")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Has results (EMP_010)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Record a result", description = "Returns the assessment with its statistics.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = RESULT_ERRORS),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PostMapping("/{id}/results")
    public ApiMessageDto<AssessmentDto> addResult(@PathVariable Long id, @Valid @RequestBody ResultForm form) {
        return ApiMessageDto.ok(service.addResult(id, form));
    }

    @Operation(summary = "Edit a result", description = "Full replacement of the result.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = RESULT_ERRORS),
            @ApiResponse(responseCode = "404", description = "No such assessment (EMP_089) or result (EMP_092)")})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PutMapping("/{id}/results/{resultId}")
    public ApiMessageDto<AssessmentDto> updateResult(@PathVariable Long id, @PathVariable Long resultId,
                                                     @Valid @RequestBody ResultForm form) {
        return ApiMessageDto.ok(service.updateResult(id, resultId, form));
    }

    @Operation(summary = "Delete a result")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such assessment (EMP_089) or result "
            + "(EMP_092)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @DeleteMapping("/{id}/results/{resultId}")
    public ApiMessageDto<AssessmentDto> deleteResult(@PathVariable Long id, @PathVariable Long resultId) {
        return ApiMessageDto.ok(service.deleteResult(id, resultId));
    }
}
