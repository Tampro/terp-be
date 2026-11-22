package com.terp.erp.document.hr;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import com.terp.erp.document.hr.AcknowledgmentService.AckDto;
import com.terp.erp.document.hr.AcknowledgmentService.AcknowledgeForm;
import com.terp.erp.document.hr.AcknowledgmentService.AssignForm;
import com.terp.erp.document.hr.AcknowledgmentService.ETab;
import com.terp.erp.document.hr.AcknowledgmentService.EditForm;
import com.terp.erp.document.hr.AcknowledgmentService.TabCounts;
import com.terp.erp.document.hr.HrDocumentService.Cards;
import com.terp.erp.document.hr.HrDocumentService.DecisionForm;
import com.terp.erp.document.hr.HrDocumentService.DocumentDto;
import com.terp.erp.document.hr.HrDocumentService.DocumentForm;
import com.terp.erp.document.hr.HrDocumentService.Download;
import com.terp.erp.document.hr.HrDocumentService.Option;
import com.terp.erp.document.hr.HrDocumentService.StatusCounts;

import java.util.List;

import static com.terp.erp.document.common.Texts.COMPANY;

/** HR Documents (§9.4.1), Acknowledgments (§9.4.3) and the employee's own acknowledgments. */
@Tag(name = "HR documents and acknowledgments")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/document")
@SecurityRequirement(name = "bearerAuth")
public class HrDocumentController {
    private static final String NO_COMPANY = "Super admin without companyId (DOC_001)";
    private static final String DOC_NOT_FOUND = "No such document in your company (DOC_009)";
    private static final String ACK_NOT_FOUND = "No such acknowledgment in your company (DOC_011)";
    private static final String FORM_ERRORS = "Category not in the company (DOC_002), inactive category (DOC_015), "
            + "expiry before effective (DOC_010), file not in the media library (DOC_008)";

    private final HrDocumentService documents;
    private final AcknowledgmentService acks;

    public record StatusForm(@NotNull HrDocument.EStatus status) {
    }

    // ------------------------------------------------------------------ documents

    @Operation(summary = "HR documents of the company", description = "keyword = title or description; categoryId; "
            + "status = the tab. status in the result is effective: EXPIRED once past the expiry date unless "
            + "archived (storedStatus = as set).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_VIEW')")
    @GetMapping("/documents")
    public ApiMessageDto<ResponseListDto<DocumentDto>> documents(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) HrDocument.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(documents.search(companyId, keyword, categoryId, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs", description = "Effective statuses; same filters as the list.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_VIEW')")
    @GetMapping("/documents/status-counts")
    public ApiMessageDto<StatusCounts> documentCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId) {
        return ApiMessageDto.ok(documents.statusCounts(companyId, keyword, categoryId));
    }

    @Operation(summary = "Summary cards", description = "Total, Published (with %), Expiring Soon (published, "
            + "expiring within 30 days), Needs Acknowledgment (documents requiring it).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_VIEW')")
    @GetMapping("/documents/cards")
    public ApiMessageDto<Cards> cards(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(documents.cards(companyId));
    }

    @Operation(summary = "Document dropdown (Assign Document)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_VIEW')")
    @GetMapping("/documents/options")
    public ApiMessageDto<List<Option>> documentOptions(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(documents.options(companyId));
    }

    @Operation(summary = "Document details (View)", description = "With the file name, type and size.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = DOC_NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_VIEW')")
    @GetMapping("/documents/{id}")
    public ApiMessageDto<DocumentDto> document(@PathVariable Long id) {
        return ApiMessageDto.ok(documents.get(id));
    }

    @Operation(summary = "Download", description = "A signed, time-limited link of the media library (needs "
            + "MEDIA_VIEW too). Without DOCUMENT_MANAGE: only an APPROVED or PUBLISHED document, and not while you "
            + "still have a pending acknowledgment for it; DOCUMENT_MANAGE holders download in any status.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "The file is no longer in the media library (DOC_008), "
                    + "not approved or published (DOC_025), acknowledge it first (DOC_026)"),
            @ApiResponse(responseCode = "404", description = DOC_NOT_FOUND),
            @ApiResponse(responseCode = "503", description = "media-service unavailable (DOC_007)")})
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_VIEW')")
    @GetMapping("/documents/{id}/download")
    public ApiMessageDto<Download> download(@PathVariable Long id) {
        return ApiMessageDto.ok(documents.download(id));
    }

    @Operation(summary = "Upload Document", description = "Upload the file to the media library first, then send "
            + "its id. Created DRAFT; Requires Acknowledgement omitted = the category's Mandatory flag.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS + ", " + NO_COMPANY),
            @ApiResponse(responseCode = "503", description = "media-service unavailable (DOC_007)")})
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @PostMapping("/documents")
    public ApiMessageDto<DocumentDto> createDocument(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody DocumentForm form) {
        return ApiMessageDto.ok(documents.create(companyId, form));
    }

    @Operation(summary = "Edit a document", description = "Full replacement of the details; Requires Acknowledgement "
            + "omitted = kept; the status changes through Update Status.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS),
            @ApiResponse(responseCode = "404", description = DOC_NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @PutMapping("/documents/{id}")
    public ApiMessageDto<DocumentDto> updateDocument(@PathVariable Long id, @Valid @RequestBody DocumentForm form) {
        return ApiMessageDto.ok(documents.update(id, form));
    }

    @Operation(summary = "Approve a document", description = "PENDING only; optional comment. Records who and when.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (DOC_024)"),
            @ApiResponse(responseCode = "404", description = DOC_NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @PostMapping("/documents/{id}/approve")
    public ApiMessageDto<DocumentDto> approve(@PathVariable Long id,
                                              @Valid @RequestBody(required = false) DecisionForm form) {
        return ApiMessageDto.ok(documents.approve(id, form));
    }

    @Operation(summary = "Reject a document", description = "PENDING only; optional comment. Records who and when.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (DOC_024)"),
            @ApiResponse(responseCode = "404", description = DOC_NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @PostMapping("/documents/{id}/reject")
    public ApiMessageDto<DocumentDto> reject(@PathVariable Long id,
                                             @Valid @RequestBody(required = false) DecisionForm form) {
        return ApiMessageDto.ok(documents.reject(id, form));
    }

    @Operation(summary = "Update Status", description = "DRAFT, PENDING, UNDER_REVIEW, APPROVED, REJECTED, PUBLISHED, "
            + "ARCHIVED or EXPIRED. Prefer approve / reject, which record who decided.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = DOC_NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @PutMapping("/documents/{id}/status")
    public ApiMessageDto<DocumentDto> documentStatus(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(documents.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a document", description = "Its acknowledgments disappear with it.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = DOC_NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @DeleteMapping("/documents/{id}")
    public ApiMessageDto<Void> deleteDocument(@PathVariable Long id) {
        documents.delete(id);
        return ApiMessageDto.ok(null);
    }

    // ------------------------------------------------------------------ acknowledgments

    @Operation(summary = "Acknowledgments of the company", description = "keyword = employee name or email; "
            + "documentId, employeeId (the User filter); tab omitted = All. OVERDUE = pending past the due date "
            + "(left out of PENDING).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_VIEW')")
    @GetMapping("/acknowledgments")
    public ApiMessageDto<ResponseListDto<AckDto>> acknowledgments(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long documentId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) ETab tab,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(acks.search(companyId, keyword, documentId, employeeId, tab,
                pageable)));
    }

    @Operation(summary = "Counts of the tabs (All, Pending, Acknowledged, Overdue, Exempted)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_VIEW')")
    @GetMapping("/acknowledgments/tab-counts")
    public ApiMessageDto<TabCounts> ackCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long documentId,
            @RequestParam(required = false) Long employeeId) {
        return ApiMessageDto.ok(acks.tabCounts(companyId, keyword, documentId, employeeId));
    }

    @Operation(summary = "Acknowledgment details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = ACK_NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_VIEW')")
    @GetMapping("/acknowledgments/{id}")
    public ApiMessageDto<AckDto> acknowledgment(@PathVariable Long id) {
        return ApiMessageDto.ok(acks.get(id));
    }

    @Operation(summary = "Assign Document", description = "The employee is checked in employee-service as you; their "
            + "login account is remembered so they can acknowledge it themself.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Document not in the company (DOC_009), archived "
                    + "(DOC_016), already open for the employee (DOC_012), not an employee (DOC_013), " + NO_COMPANY),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (DOC_006)")})
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @PostMapping("/acknowledgments")
    public ApiMessageDto<AckDto> assign(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                        @Valid @RequestBody AssignForm form) {
        return ApiMessageDto.ok(acks.assign(companyId, form));
    }

    @Operation(summary = "Edit an acknowledgment", description = "Due date, note (full replacement) and PENDING ↔ "
            + "EXEMPTED; an acknowledged one keeps its status.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = ACK_NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @PutMapping("/acknowledgments/{id}")
    public ApiMessageDto<AckDto> editAck(@PathVariable Long id, @Valid @RequestBody EditForm form) {
        return ApiMessageDto.ok(acks.edit(id, form));
    }

    @Operation(summary = "Acknowledge (on the employee's behalf)", description = "Pending only, with a note.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (DOC_014)"),
            @ApiResponse(responseCode = "404", description = ACK_NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @PostMapping("/acknowledgments/{id}/acknowledge")
    public ApiMessageDto<AckDto> acknowledge(@PathVariable Long id, @Valid @RequestBody AcknowledgeForm form) {
        return ApiMessageDto.ok(acks.acknowledge(id, form));
    }

    @Operation(summary = "Delete an acknowledgment requirement")
    @ApiResponses(@ApiResponse(responseCode = "404", description = ACK_NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('DOCUMENT_MANAGE')")
    @DeleteMapping("/acknowledgments/{id}")
    public ApiMessageDto<Void> deleteAck(@PathVariable Long id) {
        acks.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "My acknowledgments", description = "Documents assigned to the signed-in employee (any "
            + "company user; no document permission needed).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Not a company user (DOC_001)"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my-acknowledgments")
    public ApiMessageDto<ResponseListDto<AckDto>> mine(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(acks.mine(pageable)));
    }

    @Operation(summary = "Download my document", description = "Only the employee it was assigned to, once "
            + "acknowledged, and only an APPROVED or PUBLISHED document (no document permission needed).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not acknowledged yet (DOC_026), not approved or "
                    + "published (DOC_025), file gone (DOC_008), not a company user (DOC_001)"),
            @ApiResponse(responseCode = "404", description = "Not one of yours (DOC_011), document gone (DOC_009)"),
            @ApiResponse(responseCode = "503", description = "media-service unavailable (DOC_007)")})
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my-acknowledgments/{id}/download")
    public ApiMessageDto<Download> downloadMine(@PathVariable Long id) {
        return ApiMessageDto.ok(acks.downloadMine(id));
    }

    @Operation(summary = "Acknowledge my document", description = "Only the employee it was assigned to; pending only.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not pending (DOC_014)"),
            @ApiResponse(responseCode = "404", description = "Not one of yours (DOC_011)")})
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/my-acknowledgments/{id}/acknowledge")
    public ApiMessageDto<AckDto> acknowledgeMine(@PathVariable Long id, @Valid @RequestBody AcknowledgeForm form) {
        return ApiMessageDto.ok(acks.acknowledgeMine(id, form));
    }
}
