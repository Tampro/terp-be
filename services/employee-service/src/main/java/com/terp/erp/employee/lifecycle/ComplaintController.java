package com.terp.erp.employee.lifecycle;

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
import com.terp.erp.employee.lifecycle.ComplaintService.AssignForm;
import com.terp.erp.employee.lifecycle.ComplaintService.ComplaintDto;
import com.terp.erp.employee.lifecycle.ComplaintService.ComplaintForm;
import com.terp.erp.employee.lifecycle.ComplaintService.FollowUpForm;
import com.terp.erp.employee.lifecycle.ComplaintService.ResolveForm;
import com.terp.erp.employee.lifecycle.ComplaintService.StatusCounts;
import com.terp.erp.employee.lifecycle.ComplaintService.StatusForm;

import java.time.LocalDate;

import static com.terp.erp.employee.lifecycle.AwardController.COMPANY;

@Tag(name = "Complaints")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle/complaints")
@SecurityRequirement(name = "bearerAuth")
public class ComplaintController {
    private static final String KEYWORD = "Matches the subject, the person complained against, or the complainant "
            + "unless anonymous";
    private static final String NOT_FOUND = "No such complaint in your company (EMP_067)";

    private final ComplaintService service;

    @Operation(summary = "Complaints of the company", description = "typeId = entry of /catalogs/complaint-types; "
            + "from / to = on the complaint date; status = the tab. An anonymous complaint has complainant = null "
            + "for callers without LIFECYCLE_MANAGE.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ComplaintDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) EmployeeComplaint.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "complaintDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, typeId, from, to, status,
                pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Pending, In Review, Assigned, In Progress, Resolved)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, typeId, from, to));
    }

    @Operation(summary = "Complaint details", description = "With assignment, resolution, follow-up and a signed "
            + "document link.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ComplaintDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a complaint (status PENDING)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Complainant = against (EMP_068), type not in the "
                    + "company's list (EMP_050) or inactive (EMP_054), file (EMP_012)"),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PostMapping
    public ApiMessageDto<ComplaintDto> create(@Valid @RequestBody ComplaintForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a complaint", description = "Full replacement of the form, only while PENDING; status, "
            + "assignment, resolution and follow-up are kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "No longer PENDING (EMP_102), EMP_068, EMP_050, EMP_054, "
                    + "EMP_012; the handler would become the accused (EMP_069)"),
            @ApiResponse(responseCode = "404", description = "No such complaint (EMP_067) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ComplaintDto> update(@PathVariable Long id, @Valid @RequestBody ComplaintForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Change Status: PENDING -> IN_REVIEW, ASSIGNED -> IN_PROGRESS",
            description = "ASSIGNED is set by assign, RESOLVED by resolve; any other move is refused (EMP_096).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Move not allowed (EMP_096)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<ComplaintDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Assign to a handler", description = "Sets ASSIGNED; from PENDING, IN_REVIEW, ASSIGNED "
            + "(re-assign) or IN_PROGRESS (re-assign), not once RESOLVED (EMP_096).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Handler is the accused (EMP_069), deadline before the "
                    + "complaint date (EMP_052), already resolved (EMP_096)"),
            @ApiResponse(responseCode = "404", description = "No such complaint (EMP_067) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/assign")
    public ApiMessageDto<ComplaintDto> assign(@PathVariable Long id, @Valid @RequestBody AssignForm form) {
        return ApiMessageDto.ok(service.assign(id, form));
    }

    @Operation(summary = "Resolve", description = "Full replacement of the resolution and follow-up action/date; "
            + "sets RESOLVED. From IN_REVIEW, ASSIGNED, IN_PROGRESS or RESOLVED (correction), not PENDING (EMP_096).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Resolution type not in the list (EMP_050) or inactive "
                    + "(EMP_054), resolution before the complaint / follow-up before the resolution (EMP_052), "
                    + "move not allowed (EMP_096)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/resolve")
    public ApiMessageDto<ComplaintDto> resolve(@PathVariable Long id, @Valid @RequestBody ResolveForm form) {
        return ApiMessageDto.ok(service.resolve(id, form));
    }

    @Operation(summary = "Follow-up", description = "Full replacement of follow-up action, date and feedback; only "
            + "while RESOLVED.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not resolved (EMP_070), follow-up before the "
                    + "resolution (EMP_052)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/follow-up")
    public ApiMessageDto<ComplaintDto> followUp(@PathVariable Long id, @Valid @RequestBody FollowUpForm form) {
        return ApiMessageDto.ok(service.followUp(id, form));
    }

    @Operation(summary = "Delete a complaint", description = "Only while PENDING.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "No longer PENDING (EMP_102)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
