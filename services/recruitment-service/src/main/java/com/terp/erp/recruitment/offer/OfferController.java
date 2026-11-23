package com.terp.erp.recruitment.offer;

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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.terp.erp.recruitment.offer.OfferConversionService.ConvertForm;
import com.terp.erp.recruitment.offer.OfferConversionService.Converted;
import com.terp.erp.recruitment.offer.OfferService.ApprovalForm;
import com.terp.erp.recruitment.offer.OfferService.KanbanColumn;
import com.terp.erp.recruitment.offer.OfferService.OfferDto;
import com.terp.erp.recruitment.offer.OfferService.OfferForm;
import com.terp.erp.recruitment.offer.OfferService.StatusCounts;
import com.terp.erp.recruitment.offer.OfferService.StatusForm;

import java.util.List;

@Tag(name = "Offers")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/offers")
@SecurityRequirement(name = "bearerAuth")
public class OfferController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String KEYWORD = "Matches the candidate's name or email, the job title or code";

    private final OfferService service;
    private final OfferLetterService letters;
    private final OfferConversionService conversions;

    @Operation(summary = "Offers of the company", description = "candidateId = Candidate filter, status = the tab. "
            + "Sort by offerDate, salary, startDate, expirationDate, createdDate.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<OfferDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long candidateId,
            @RequestParam(required = false) Offer.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, candidateId, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Draft, Sent, Accepted, Negotiating, Declined, Expired)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long candidateId) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, candidateId));
    }

    @Operation(summary = "Kanban: Draft, Sent, Negotiating, Accepted, Declined, Expired with up to 50 cards each",
            description = "Move a card with PUT /{id}/status.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/kanban")
    public ApiMessageDto<List<KanbanColumn>> kanban(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long candidateId) {
        return ApiMessageDto.ok(service.kanban(companyId, keyword, candidateId));
    }

    @Operation(summary = "Offer details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such offer in your company (REC_035)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<OfferDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Create an offer (status DRAFT)",
            description = "A NEW / SCREENING / INTERVIEW candidate moves to OFFER. Department and Approved By are read "
                    + "from employee-service (needs ORG_VIEW / EMPLOYEE_VIEW there).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Hired/rejected candidate (REC_036), expiration after start "
                    + "(REC_037)"),
            @ApiResponse(responseCode = "404", description = "No such candidate (REC_015) or posting (REC_006); "
                    + "department (EMP_003) or employee (EMP_001) relayed"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (REC_012)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<OfferDto> create(@Valid @RequestBody OfferForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit an offer", description = "Full replacement; status and offer date are kept; the "
            + "approval goes back to PENDING.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Final or converted offer (REC_067), REC_036, REC_037"),
            @ApiResponse(responseCode = "404", description = "REC_035, REC_015, REC_006, EMP_003, EMP_001"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (REC_012)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<OfferDto> update(@PathVariable Long id, @Valid @RequestBody OfferForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Update Status / kanban drag and drop", description = "ACCEPTED makes the candidate HIRED. "
            + "SENT needs approvalStatus APPROVED.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not approved before sending (REC_058), final "
                    + "(ACCEPTED / DECLINED / EXPIRED) or converted offer (REC_067)"),
            @ApiResponse(responseCode = "404", description = "No such offer in your company (REC_035)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<OfferDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Approve or reject an offer",
            description = "Sets approvalStatus (APPROVED / REJECTED, PENDING withdraws) and records who decided "
                    + "(approvalDecidedBy = Keycloak user id) and when. Independent of the offer status.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Final or converted offer (REC_067)"),
            @ApiResponse(responseCode = "404", description = "No such offer in your company (REC_035)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/approval")
    public ApiMessageDto<OfferDto> approval(@PathVariable Long id, @Valid @RequestBody ApprovalForm form) {
        return ApiMessageDto.ok(service.approve(id, form.approvalStatus()));
    }

    @Operation(summary = "Download the offer letter (PDF)",
            description = "The template (an offer template of the offer's company) filled from the offer. Variables: "
                    + "candidate_name, candidate_email, position, job_title, job_code, department, salary, start_date, "
                    + "offer_expiration_date, expiration_date, offer_date, benefits, company_name; any other "
                    + "declared variable prints empty.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "application/pdf attachment"),
            @ApiResponse(responseCode = "404", description = "No such offer (REC_035) or template (REC_032) in your "
                    + "company"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (REC_012)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}/letter")
    public ResponseEntity<byte[]> letter(@PathVariable Long id, @RequestParam Long templateId) {
        OfferLetterService.Letter l = letters.letter(id, templateId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(l.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(l.pdf());
    }

    @Operation(summary = "Email the offer letter to the candidate",
            description = "Fills the template like the PDF download, reduces it to plain text (at most 20 KB) and "
                    + "records the event recruitment.offer.letter_sent; notification-service emails it to the "
                    + "candidate's address (OFFER_LETTER) after this call commits, so 200 means queued, not delivered. "
                    + "Only a SENT, NEGOTIATING or ACCEPTED offer. Returns the offer id and the event id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Queued: {offerId, eventId}"),
            @ApiResponse(responseCode = "400", description = "Offer is DRAFT, DECLINED or EXPIRED (REC_068)"),
            @ApiResponse(responseCode = "404", description = "No such offer (REC_035) or template (REC_032) in your "
                    + "company"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (REC_012)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping("/{id}/letter/send")
    public ApiMessageDto<OfferLetterService.LetterSent> sendLetter(@PathVariable Long id,
                                                                   @RequestParam Long templateId) {
        return ApiMessageDto.ok(letters.send(id, templateId));
    }

    @Operation(summary = "Convert to Employee",
            description = "Only an ACCEPTED offer, once per candidate. Creates the employee in employee-service as the "
                    + "caller (needs EMPLOYEE_CREATE there and IAM_USER_CREATE in IAM) with the candidate's name, "
                    + "email (login), phone, birth date, gender and address; then the candidate gets employeeId and "
                    + "status HIRED. Nothing is saved here when employee-service refuses.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Offer not accepted (REC_060), candidate already "
                    + "converted (REC_061); relayed from employee-service: email used (EMP_007), code used (EMP_008), "
                    + "plan limit (EMP_009), branch/department/designation (EMP_002/003/004/023), IAM_010, IAM_014"),
            @ApiResponse(responseCode = "403", description = "Caller lacks EMPLOYEE_CREATE / IAM_USER_CREATE "
                    + "(COMMON_004)"),
            @ApiResponse(responseCode = "404", description = "No such offer (REC_035) or candidate (REC_015) in your "
                    + "company"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (REC_012)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping("/{id}/convert-to-employee")
    public ApiMessageDto<Converted> convert(@PathVariable Long id, @Valid @RequestBody ConvertForm form) {
        return ApiMessageDto.ok(conversions.convert(id, form));
    }

    @Operation(summary = "Delete an offer", description = "Not once ACCEPTED, DECLINED or EXPIRED.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Final or converted offer (REC_067)"),
            @ApiResponse(responseCode = "404", description = "No such offer in your company (REC_035)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
