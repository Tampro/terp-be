package com.terp.erp.crm.contract;

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
import com.terp.erp.crm.contract.ContractService.AttachmentDto;
import com.terp.erp.crm.contract.ContractService.AttachmentForm;
import com.terp.erp.crm.contract.ContractService.CommentDto;
import com.terp.erp.crm.contract.ContractService.CommentForm;
import com.terp.erp.crm.contract.ContractService.ContractDetail;
import com.terp.erp.crm.contract.ContractService.ContractForm;
import com.terp.erp.crm.contract.ContractService.ContractRow;
import com.terp.erp.crm.contract.ContractService.NoteDto;
import com.terp.erp.crm.contract.ContractService.NoteForm;
import com.terp.erp.crm.contract.ContractService.RenewalDto;
import com.terp.erp.crm.contract.ContractService.RenewalForm;
import com.terp.erp.crm.contract.ContractService.StatusCounts;
import com.terp.erp.crm.contract.ContractService.StatusForm;

import java.time.LocalDate;

@Tag(name = "CRM — contracts")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/crm/contracts")
@SecurityRequirement(name = "bearerAuth")
public class ContractController {
    private static final String WRITE_ERRORS = "End before start (CTR_006), inactive type (CTR_007), "
            + "super admin without companyId (CTR_001), validation (COMMON_001)";
    private static final String NOT_FOUND = "No such contract in your company (CTR_002)";
    private static final String VIEW = "@PermissionChecker.has('CRM_CONTRACT_VIEW')";
    private static final String MANAGE = "@PermissionChecker.has('CRM_CONTRACT_MANAGE')";
    private final ContractService service;

    @Operation(summary = "Contracts", description = "Number from the configured prefix (CON0001). Each row carries "
            + "progressPercent (elapsed share of start to end, 0..100) and daysLeft.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (CTR_001)"))
    @PreAuthorize(VIEW)
    @GetMapping
    public ApiMessageDto<ResponseListDto<ContractRow>> list(
            @Parameter(description = "Super admin only: the company to act on") @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long customerId, @RequestParam(required = false) Long contractTypeId,
            @RequestParam(required = false) Contract.EStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endTo,
            @Parameter(description = "Matches the number, subject or customer") @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, customerId, contractTypeId, status,
                startFrom, startTo, endFrom, endTo, keyword, pageable)));
    }

    @Operation(summary = "Summary cards", description = "Total, Active, Pending, Closed, Declined (not deleted).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (CTR_001)"))
    @PreAuthorize(VIEW)
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = "Super admin only: the company to act on") @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.statusCounts(companyId));
    }

    @Operation(summary = "Contract with renewals, comments, notes and attachments")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/{id}")
    public ApiMessageDto<ContractDetail> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Create a contract", description = "Status defaults to PENDING; the number is taken from "
            + "the prefix setting. The customer name is copied from accounting-service.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = WRITE_ERRORS),
            @ApiResponse(responseCode = "404", description = "No such contract type (CTR_003) or customer (accounting's code)"),
            @ApiResponse(responseCode = "503", description = "Accounting unavailable (CRM_015)")})
    @PreAuthorize(MANAGE)
    @PostMapping
    public ApiMessageDto<ContractDetail> create(@Valid @RequestBody ContractForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a contract", description = "Full replacement; the number never changes, companyId is "
            + "ignored and an omitted status keeps the current one.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = WRITE_ERRORS),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + ", contract type (CTR_003) or customer"),
            @ApiResponse(responseCode = "503", description = "Accounting unavailable (CRM_015)")})
    @PreAuthorize(MANAGE)
    @PutMapping("/{id}")
    public ApiMessageDto<ContractDetail> update(@PathVariable Long id, @Valid @RequestBody ContractForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a contract", description = "Soft delete; its number is not reused.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Change the status")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Validation (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PutMapping("/{id}/status")
    public ApiMessageDto<ContractDetail> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form));
    }

    @Operation(summary = "Duplicate a contract", description = "New PENDING contract with a new number and the same "
            + "fields; renewals, comments, notes and attachments are not copied.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/duplicate")
    public ApiMessageDto<ContractDetail> duplicate(@PathVariable Long id) {
        return ApiMessageDto.ok(service.duplicate(id));
    }

    // ---- renewals ----------------------------------------------------------------------------------------------

    @Operation(summary = "Add a renewal")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "End before start (CTR_006), validation (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/renewals")
    public ApiMessageDto<RenewalDto> addRenewal(@PathVariable Long id, @Valid @RequestBody RenewalForm form) {
        return ApiMessageDto.ok(service.addRenewal(id, form));
    }

    @Operation(summary = "Edit a renewal", description = "Full replacement.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "End before start (CTR_006), validation (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + " or renewal (CTR_008)")})
    @PreAuthorize(MANAGE)
    @PutMapping("/{id}/renewals/{renewalId}")
    public ApiMessageDto<RenewalDto> updateRenewal(@PathVariable Long id, @PathVariable Long renewalId,
                                                   @Valid @RequestBody RenewalForm form) {
        return ApiMessageDto.ok(service.updateRenewal(id, renewalId, form));
    }

    @Operation(summary = "Delete a renewal")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND + " or renewal (CTR_008)"))
    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}/renewals/{renewalId}")
    public ApiMessageDto<Void> deleteRenewal(@PathVariable Long id, @PathVariable Long renewalId) {
        service.deleteRenewal(id, renewalId);
        return ApiMessageDto.ok(null);
    }

    // ---- comments ----------------------------------------------------------------------------------------------

    @Operation(summary = "Add a comment", description = "Author name is the caller's username.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Validation (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/comments")
    public ApiMessageDto<CommentDto> addComment(@PathVariable Long id, @Valid @RequestBody CommentForm form) {
        return ApiMessageDto.ok(service.addComment(id, form));
    }

    @Operation(summary = "Delete a comment")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND + " or comment (CTR_009)"))
    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}/comments/{commentId}")
    public ApiMessageDto<Void> deleteComment(@PathVariable Long id, @PathVariable Long commentId) {
        service.deleteComment(id, commentId);
        return ApiMessageDto.ok(null);
    }

    // ---- notes -------------------------------------------------------------------------------------------------

    @Operation(summary = "Add a note")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Validation (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/notes")
    public ApiMessageDto<NoteDto> addNote(@PathVariable Long id, @Valid @RequestBody NoteForm form) {
        return ApiMessageDto.ok(service.addNote(id, form));
    }

    @Operation(summary = "Edit a note", description = "Full replacement.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Validation (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + " or note (CTR_010)")})
    @PreAuthorize(MANAGE)
    @PutMapping("/{id}/notes/{noteId}")
    public ApiMessageDto<NoteDto> updateNote(@PathVariable Long id, @PathVariable Long noteId,
                                             @Valid @RequestBody NoteForm form) {
        return ApiMessageDto.ok(service.updateNote(id, noteId, form));
    }

    @Operation(summary = "Delete a note")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND + " or note (CTR_010)"))
    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}/notes/{noteId}")
    public ApiMessageDto<Void> deleteNote(@PathVariable Long id, @PathVariable Long noteId) {
        service.deleteNote(id, noteId);
        return ApiMessageDto.ok(null);
    }

    // ---- attachments -------------------------------------------------------------------------------------------

    @Operation(summary = "Attach a media library file", description = "The file must belong to the contract's company.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a file of the company (CTR_012), validation (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND),
            @ApiResponse(responseCode = "503", description = "Media unavailable (CRM_016)")})
    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/attachments")
    public ApiMessageDto<AttachmentDto> addAttachment(@PathVariable Long id, @Valid @RequestBody AttachmentForm form) {
        return ApiMessageDto.ok(service.addAttachment(id, form));
    }

    @Operation(summary = "Remove an attachment", description = "The media file itself stays in the library.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND + " or attachment (CTR_011)"))
    @PreAuthorize(MANAGE)
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public ApiMessageDto<Void> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        service.deleteAttachment(id, attachmentId);
        return ApiMessageDto.ok(null);
    }
}
