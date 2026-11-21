package com.terp.erp.recruitment.question;

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
import com.terp.erp.recruitment.question.CustomQuestionService.QuestionDto;
import com.terp.erp.recruitment.question.CustomQuestionService.QuestionForm;

import java.util.List;

@Tag(name = "Custom Questions")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/custom-questions")
@SecurityRequirement(name = "bearerAuth")
public class CustomQuestionController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final CustomQuestionService service;

    @Operation(summary = "Custom questions of the company", description = "keyword matches the question.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<QuestionDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, pageable)));
    }

    @Operation(summary = "Every custom question, for the job posting form")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<QuestionDto>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Custom question details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such question in your company (REC_003)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<QuestionDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a custom question")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<QuestionDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                             @Valid @RequestBody QuestionForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a custom question (full replacement)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such question in your company (REC_003)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<QuestionDto> update(@PathVariable Long id, @Valid @RequestBody QuestionForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a custom question")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such question in your company (REC_003)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
