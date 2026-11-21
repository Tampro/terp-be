package com.terp.erp.recruitment.candidate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.recruitment.candidate.CandidateDtos.CandidateDetail;
import com.terp.erp.recruitment.candidate.CandidateDtos.CandidateForm;
import com.terp.erp.recruitment.candidate.CandidateDtos.CandidateRow;
import com.terp.erp.recruitment.candidate.CandidateDtos.KanbanColumn;
import com.terp.erp.recruitment.candidate.CandidateDtos.StatusCounts;
import com.terp.erp.recruitment.candidate.CandidateDtos.StatusForm;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "Candidates")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/candidates")
@SecurityRequirement(name = "bearerAuth")
public class CandidateController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String KEYWORD = "Matches first name, last name, full name or email";

    private final CandidateService service;

    @Operation(summary = "Candidates of the company",
            description = "jobPostingId = Job filter, sourceId = Source filter, status = the tab. Sort by "
                    + "applicationDate, createdDate, lastName.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<CandidateRow>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) Candidate.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, jobPostingId, sourceId, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, New, Screening, Interview, Offer, Hired, Rejected)",
            description = "Same filters as the list, without status.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) Long sourceId) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, jobPostingId, sourceId));
    }

    @Operation(summary = "Kanban: the six stages in order, each with its count and up to 50 latest cards",
            description = "Move a card with PUT /{id}/status.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/kanban")
    public ApiMessageDto<List<KanbanColumn>> kanban(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long jobPostingId,
            @RequestParam(required = false) Long sourceId) {
        return ApiMessageDto.ok(service.kanban(companyId, keyword, jobPostingId, sourceId));
    }

    @Operation(summary = "Candidate details", description = "Applied position, answers, resume link (needs a token).")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such candidate in your company (REC_015)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<CandidateDetail> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Download the resume")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such candidate (REC_015) or no resume (REC_021)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}/resume")
    public ResponseEntity<FileSystemResource> resume(@PathVariable Long id) {
        return download(service.resume(id));
    }

    private static ResponseEntity<FileSystemResource> download(CandidateService.Resume r) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(r.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(r.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(r.path()));
    }

    @Operation(summary = "Download the cover letter file sent from the career page")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such candidate (REC_015) or no cover letter "
            + "(REC_053)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}/cover-letter")
    public ResponseEntity<FileSystemResource> coverLetter(@PathVariable Long id) {
        return download(service.coverLetter(id));
    }

    @Operation(summary = "Add a candidate by hand (status NEW)",
            description = "One application per email and posting; the posting's required questions must be answered. "
                    + "Upload the resume with PUT /{id}/resume.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already applied (REC_016), required question not answered "
                    + "(REC_017), question of another posting (REC_018)"),
            @ApiResponse(responseCode = "404", description = "No such posting (REC_006) or source (REC_001)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<CandidateDetail> create(@Valid @RequestBody CandidateForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Upload or replace the resume (PDF, DOC, DOCX, at most 5 MB)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Type not allowed or content not matching (REC_019), "
                    + "too large (REC_020)"),
            @ApiResponse(responseCode = "404", description = "No such candidate in your company (REC_015)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping(value = "/{id}/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiMessageDto<CandidateDetail> uploadResume(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ApiMessageDto.ok(service.uploadResume(id, file));
    }

    @Operation(summary = "Update the stage (Update Status / kanban drag and drop)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such candidate in your company (REC_015)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<CandidateDetail> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a candidate", description = "Their interviews, offers and assessments are deleted too.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such candidate in your company (REC_015)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
