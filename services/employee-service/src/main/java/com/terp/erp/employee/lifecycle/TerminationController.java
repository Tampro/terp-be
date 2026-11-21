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
import com.terp.erp.employee.lifecycle.TerminationService.StatusCounts;
import com.terp.erp.employee.lifecycle.TerminationService.StatusForm;
import com.terp.erp.employee.lifecycle.TerminationService.TerminationDto;
import com.terp.erp.employee.lifecycle.TerminationService.TerminationForm;

import java.time.LocalDate;

import static com.terp.erp.employee.lifecycle.AwardController.COMPANY;
import static com.terp.erp.employee.lifecycle.ResignationController.KEYWORD;

@Tag(name = "Terminations")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle/terminations")
@SecurityRequirement(name = "bearerAuth")
public class TerminationController {
    private final TerminationService service;

    @Operation(summary = "Terminations of the company", description = "typeId = entry of "
            + "/catalogs/termination-types; from / to = on the termination date; status = the tab.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TerminationDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) EmployeeTermination.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "terminationDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, typeId, from, to, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Planned, In Progress, Completed)")
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

    @Operation(summary = "Termination details", description = "With the exit interview and a signed document link.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such termination in your company (EMP_056)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<TerminationDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a termination (status PLANNED)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Employee already terminated (EMP_058), type not in the "
                    + "company's list (EMP_050) or inactive (EMP_054), termination date before the notice date "
                    + "(EMP_052), file (EMP_012)"),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PostMapping
    public ApiMessageDto<TerminationDto> create(@Valid @RequestBody TerminationForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a termination", description = "Full replacement of the form until completed; status "
            + "and exit interview are kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Completed (EMP_057), EMP_058, EMP_050, EMP_054, "
                    + "EMP_052, EMP_012"),
            @ApiResponse(responseCode = "404", description = "No such termination (EMP_056) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<TerminationDto> update(@PathVariable Long id, @Valid @RequestBody TerminationForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Change Status (Planned, In Progress, Completed) and exit interview",
            description = "COMPLETED sets the employee TERMINATED and locks their login now, whatever the termination "
                    + "date; the record is then locked. Exit interview not conducted clears date and feedback.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already completed (EMP_057)"),
            @ApiResponse(responseCode = "403", description = "Locking the login was refused by IAM"),
            @ApiResponse(responseCode = "404", description = "No such termination in your company (EMP_056)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<TerminationDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form));
    }

    @Operation(summary = "Delete a termination", description = "Not once completed.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Completed (EMP_057)"),
            @ApiResponse(responseCode = "404", description = "No such termination in your company (EMP_056)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
