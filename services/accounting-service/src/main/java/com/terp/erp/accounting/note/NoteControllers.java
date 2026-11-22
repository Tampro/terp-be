package com.terp.erp.accounting.note;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.accounting.note.NoteService.NoteDto;
import com.terp.erp.accounting.note.NoteService.NoteForm;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;

import java.time.LocalDate;

/** Credit notes and debit notes: same endpoints, the class decides the kind (ERP manual 12.5). */
final class NoteControllers {
    static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    static final String NOT_FOUND = "No such note in your company (ACC_061)";

    private NoteControllers() {
    }

    @SecurityRequirement(name = "bearerAuth")
    public abstract static class Base {
        private final NoteService service;
        private final ENoteKind kind;

        Base(NoteService service, ENoteKind kind) {
            this.service = service;
            this.kind = kind;
        }

        @Operation(summary = "Notes", description = "partyId = customer (credit notes) or vendor (debit notes); "
                + "from/to on the note date.")
        @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (ACC_001)"))
        @PreAuthorize("@PermissionChecker.has('NOTE_VIEW')")
        @GetMapping
        public ApiMessageDto<ResponseListDto<NoteDto>> list(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                @RequestParam(required = false) Long partyId, @RequestParam(required = false) ENoteStatus status,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
                Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.search(kind, companyId, partyId, status, from, to,
                    pageable)));
        }

        @Operation(summary = "Note details")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
        @PreAuthorize("@PermissionChecker.has('NOTE_VIEW')")
        @GetMapping("/{id}")
        public ApiMessageDto<NoteDto> get(@PathVariable Long id) {
            return ApiMessageDto.ok(service.get(kind, id));
        }

        @Operation(summary = "Create a note (DRAFT) from a completed return", description = "Number CN-/DN-YYYY-MM-NNN; "
                + "total and tax are the return's (read from commerce-service: the caller needs SALES_VIEW / "
                + "PURCHASE_VIEW there). One live note per return.")
        @ApiResponses({
                @ApiResponse(responseCode = "400", description = "Return not completed (ACC_063), return already has "
                        + "a note (ACC_064), super admin without companyId (ACC_001), validation (COMMON_001)"),
                @ApiResponse(responseCode = "404", description = "Return of another company (SAL_012 / PUR_008 "
                        + "relayed, or ACC_061)"),
                @ApiResponse(responseCode = "503", description = "Commerce unavailable (ACC_060)")})
        @PreAuthorize("@PermissionChecker.has('NOTE_MANAGE')")
        @PostMapping
        public ApiMessageDto<NoteDto> create(
                @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                @Valid @RequestBody NoteForm form) {
            return ApiMessageDto.ok(service.create(kind, companyId, form));
        }

        @Operation(summary = "Delete a draft note", description = "The return can then get a new note.")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a draft (ACC_062)"),
                @ApiResponse(responseCode = "404", description = NOT_FOUND)})
        @PreAuthorize("@PermissionChecker.has('NOTE_MANAGE')")
        @DeleteMapping("/{id}")
        public ApiMessageDto<Void> delete(@PathVariable Long id) {
            service.delete(kind, id);
            return ApiMessageDto.ok(null);
        }

        @Operation(summary = "Approve: DRAFT → APPROVED", description = "Posts the journal entry (credit note: Dr sales "
                + "revenue + output VAT / Cr receivable; debit note: Dr payable / Cr inventory + input VAT). The "
                + "balance can then be applied in payments.")
        @ApiResponses({@ApiResponse(responseCode = "400", description = "Not a draft (ACC_062), ledger codes "
                + "(unmapped system account, closed period)"),
                @ApiResponse(responseCode = "404", description = NOT_FOUND)})
        @PreAuthorize("@PermissionChecker.has('NOTE_APPROVE')")
        @PostMapping("/{id}/approve")
        public ApiMessageDto<NoteDto> approve(@PathVariable Long id) {
            return ApiMessageDto.ok(service.approve(kind, id));
        }
    }

    @Tag(name = "Credit notes")
    @RestController
    @RequestMapping("/api/v1/accounting/credit-notes")
    public static class CreditNoteController extends Base {
        public CreditNoteController(NoteService service) {
            super(service, ENoteKind.CREDIT);
        }
    }

    @Tag(name = "Debit notes")
    @RestController
    @RequestMapping("/api/v1/accounting/debit-notes")
    public static class DebitNoteController extends Base {
        public DebitNoteController(NoteService service) {
            super(service, ENoteKind.DEBIT);
        }
    }
}
