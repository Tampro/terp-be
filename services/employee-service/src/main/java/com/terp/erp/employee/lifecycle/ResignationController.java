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
import com.terp.erp.employee.lifecycle.ResignationService.ResignationDto;
import com.terp.erp.employee.lifecycle.ResignationService.ResignationForm;
import com.terp.erp.employee.lifecycle.ResignationService.StatusCounts;
import com.terp.erp.employee.lifecycle.ResignationService.StatusForm;

import java.time.LocalDate;

import static com.terp.erp.employee.lifecycle.AwardController.COMPANY;

@Tag(name = "Resignations")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle/resignations")
@SecurityRequirement(name = "bearerAuth")
public class ResignationController {
    static final String KEYWORD = "Matches the employee's name, email or employee no., or the reason";

    private final ResignationService service;

    @Operation(summary = "Resignations of the company", description = "from / to = on the resignation date; "
            + "status = the tab.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ResignationDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) EmployeeResignation.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "resignationDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, employeeId, from, to, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Pending, Accepted, Rejected)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, employeeId, from, to));
    }

    @Operation(summary = "Resignation details", description = "With the exit interview and a signed document link.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such resignation in your company (EMP_055)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ResignationDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a resignation (status PENDING)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Employee already terminated (EMP_058), last working "
                    + "day before the resignation date (EMP_052), file (EMP_012)"),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PostMapping
    public ApiMessageDto<ResignationDto> create(@Valid @RequestBody ResignationForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a resignation", description = "Full replacement of the form while PENDING; status "
            + "and exit interview are kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (EMP_046), EMP_058, EMP_052, EMP_012"),
            @ApiResponse(responseCode = "404", description = "No such resignation (EMP_055) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ResignationDto> update(@PathVariable Long id, @Valid @RequestBody ResignationForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Decide: ACCEPTED or REJECTED (only from PENDING), with the exit interview",
            description = "ACCEPTED sets the employee TERMINATED and locks their login now, whatever the last "
                    + "working day; the record is then locked. Exit interview not conducted clears date and feedback.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Move not allowed, e.g. already decided (EMP_096)"),
            @ApiResponse(responseCode = "403", description = "Locking the login was refused by IAM"),
            @ApiResponse(responseCode = "404", description = "No such resignation in your company (EMP_055)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<ResignationDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form));
    }

    @Operation(summary = "Exit interview (full replacement of its three fields)",
            description = "At any status; not conducted clears the date and the feedback.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such resignation in your company (EMP_055)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/exit-interview")
    public ApiMessageDto<ResignationDto> exitInterview(@PathVariable Long id,
            @Valid @RequestBody ResignationService.ExitInterviewForm form) {
        return ApiMessageDto.ok(service.exitInterview(id, form));
    }

    @Operation(summary = "Delete a resignation", description = "Only while PENDING.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (EMP_046)"),
            @ApiResponse(responseCode = "404", description = "No such resignation in your company (EMP_055)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
