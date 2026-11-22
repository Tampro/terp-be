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
import com.terp.erp.employee.lifecycle.TransferService.StatusCounts;
import com.terp.erp.employee.lifecycle.TransferService.TransferDto;
import com.terp.erp.employee.lifecycle.TransferService.TransferForm;

import java.time.LocalDate;

import static com.terp.erp.employee.lifecycle.AwardController.COMPANY;
import static com.terp.erp.employee.lifecycle.AwardController.KEYWORD;

@Tag(name = "Transfers")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle/transfers")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {
    private static final String CHAIN = "Branch (EMP_002), department (EMP_003), designation (EMP_004) not in the "
            + "chain, missing upper level (EMP_023), inactive (EMP_029/030/031), nothing changes (EMP_048)";

    private final TransferService service;

    @Operation(summary = "Transfers of the company", description = "departmentId matches the department left or "
            + "joined; Date From / To on the transfer date; status = the tab. changes = the Transfer Type column.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TransferDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ETransferStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "transferDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, departmentId, from, to, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Pending, Approved, In Progress, Rejected, Cancelled)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, departmentId, from, to));
    }

    @Operation(summary = "Transfer details", description = "With a signed link to the document.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such transfer in your company (EMP_045)"))
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<TransferDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a transfer (Pending)", description = "Omitted to-values keep the employee's current "
            + "branch / department / designation.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = CHAIN + ", effective before transfer date (EMP_049), "
                    + "file (EMP_012)"),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PostMapping
    public ApiMessageDto<TransferDto> create(@Valid @RequestBody TransferForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit an open transfer", description = "Full replacement; while PENDING or IN_PROGRESS.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (EMP_046), " + CHAIN + ", EMP_049, "
                    + "EMP_012"),
            @ApiResponse(responseCode = "404", description = "No such transfer (EMP_045) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<TransferDto> update(@PathVariable Long id, @Valid @RequestBody TransferForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Approve: the employee moves to the new placement now",
            description = "From PENDING or IN_PROGRESS.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Move not allowed (EMP_096); the placement no longer "
                    + "valid or no change since the employee moved: " + CHAIN),
            @ApiResponse(responseCode = "404", description = "No such transfer in your company (EMP_045)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/approve")
    public ApiMessageDto<TransferDto> approve(@PathVariable Long id) {
        return ApiMessageDto.ok(service.approve(id));
    }

    @Operation(summary = "Reject", description = "From PENDING or IN_PROGRESS.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Move not allowed (EMP_096)"),
            @ApiResponse(responseCode = "404", description = "No such transfer in your company (EMP_045)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/reject")
    public ApiMessageDto<TransferDto> reject(@PathVariable Long id) {
        return ApiMessageDto.ok(service.reject(id));
    }

    @Operation(summary = "Start processing (PENDING -> IN_PROGRESS)", description = "Still editable and decidable.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Move not allowed (EMP_096)"),
            @ApiResponse(responseCode = "404", description = "No such transfer in your company (EMP_045)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/start")
    public ApiMessageDto<TransferDto> start(@PathVariable Long id) {
        return ApiMessageDto.ok(service.start(id));
    }

    @Operation(summary = "Cancel (from PENDING or IN_PROGRESS)", description = "The employee stays where they are.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Move not allowed (EMP_096)"),
            @ApiResponse(responseCode = "404", description = "No such transfer in your company (EMP_045)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @PutMapping("/{id}/cancel")
    public ApiMessageDto<TransferDto> cancel(@PathVariable Long id) {
        return ApiMessageDto.ok(service.cancel(id));
    }

    @Operation(summary = "Delete a pending transfer")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already decided (EMP_046)"),
            @ApiResponse(responseCode = "404", description = "No such transfer in your company (EMP_045)")})
    @PreAuthorize("@PermissionChecker.has('LIFECYCLE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
