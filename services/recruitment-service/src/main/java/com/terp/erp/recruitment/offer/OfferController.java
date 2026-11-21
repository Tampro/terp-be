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

    @Operation(summary = "Edit an offer", description = "Full replacement; status and offer date are kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "REC_036, REC_037"),
            @ApiResponse(responseCode = "404", description = "REC_035, REC_015, REC_006, EMP_003, EMP_001"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (REC_012)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<OfferDto> update(@PathVariable Long id, @Valid @RequestBody OfferForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Update Status / kanban drag and drop", description = "ACCEPTED makes the candidate HIRED.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such offer in your company (REC_035)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<OfferDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete an offer")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such offer in your company (REC_035)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
