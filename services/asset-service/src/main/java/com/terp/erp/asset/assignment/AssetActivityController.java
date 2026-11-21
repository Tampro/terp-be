package com.terp.erp.asset.assignment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.asset.assignment.AssetAssignmentService.AssignForm;
import com.terp.erp.asset.assignment.AssetAssignmentService.AssignmentDto;
import com.terp.erp.asset.assignment.AssetAssignmentService.ReturnForm;
import com.terp.erp.asset.maintenance.AssetMaintenanceService;
import com.terp.erp.asset.maintenance.AssetMaintenanceService.MaintenanceDto;
import com.terp.erp.asset.maintenance.AssetMaintenanceService.MaintenanceForm;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;

/** Assign, Return, Maintenance and the Assignments / Maintenance tabs of the Asset Details page. */
@Tag(name = "Asset assignments and maintenance")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/asset/assets/{id}")
@SecurityRequirement(name = "bearerAuth")
public class AssetActivityController {
    private static final String NOT_FOUND = "No such asset in your company (AST_005)";

    private final AssetAssignmentService assignments;
    private final AssetMaintenanceService maintenance;

    @Operation(summary = "Assign the asset to an employee", description = "AVAILABLE assets only; the asset becomes "
            + "ASSIGNED and takes the checkout condition. The employee is checked in employee-service as you.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not available (AST_018), expected return before "
                    + "checkout (AST_019)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + ", no such employee (EMP_001)"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (AST_007)")})
    @PreAuthorize("@PermissionChecker.has('ASSET_MANAGE')")
    @PostMapping("/assign")
    public ApiMessageDto<AssignmentDto> assign(@PathVariable Long id, @Valid @RequestBody AssignForm form) {
        return ApiMessageDto.ok(assignments.assign(id, form));
    }

    @Operation(summary = "Return (check in) the asset", description = "Closes the open assignment; the asset becomes "
            + "AVAILABLE and takes the check-in condition.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not assigned (AST_020), check-in before checkout "
                    + "(AST_021)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('ASSET_MANAGE')")
    @PostMapping("/return")
    public ApiMessageDto<AssignmentDto> giveBack(@PathVariable Long id, @Valid @RequestBody ReturnForm form) {
        return ApiMessageDto.ok(assignments.giveBack(id, form));
    }

    @Operation(summary = "Assignment History", description = "Newest checkout first. Needs EMPLOYEE_VIEW too "
            + "(photos come from employee-service).")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('ASSET_VIEW')")
    @GetMapping("/assignments")
    public ApiMessageDto<ResponseListDto<AssignmentDto>> assignments(@PathVariable Long id,
                                                                     @ParameterObject @PageableDefault(size = 20)
                                                                     Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(assignments.history(id, pageable)));
    }

    @Operation(summary = "Schedule maintenance", description = "Start today or earlier = IN_PROGRESS and the asset "
            + "becomes MAINTENANCE (an assigned asset must be returned first); a later start = SCHEDULED.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Disposed (AST_022), assigned (AST_023), end before "
                    + "start (AST_024)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('ASSET_MANAGE')")
    @PostMapping("/maintenance")
    public ApiMessageDto<MaintenanceDto> createMaintenance(@PathVariable Long id,
                                                           @Valid @RequestBody MaintenanceForm form) {
        return ApiMessageDto.ok(maintenance.create(id, form));
    }

    @Operation(summary = "Edit a maintenance record", description = "Full replacement of a SCHEDULED or IN_PROGRESS "
            + "record; status omitted = kept. IN_PROGRESS puts the asset in MAINTENANCE; COMPLETED or CANCELLED "
            + "makes it AVAILABLE again.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already completed or cancelled (AST_026), assigned "
                    + "(AST_023), disposed (AST_022), end before start (AST_024)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + ", no such record (AST_025)")})
    @PreAuthorize("@PermissionChecker.has('ASSET_MANAGE')")
    @PutMapping("/maintenance/{maintenanceId}")
    public ApiMessageDto<MaintenanceDto> updateMaintenance(@PathVariable Long id, @PathVariable Long maintenanceId,
                                                           @Valid @RequestBody MaintenanceForm form) {
        return ApiMessageDto.ok(maintenance.update(id, maintenanceId, form));
    }

    @Operation(summary = "Maintenance History", description = "Newest start first.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('ASSET_VIEW')")
    @GetMapping("/maintenance")
    public ApiMessageDto<ResponseListDto<MaintenanceDto>> maintenance(@PathVariable Long id,
                                                                      @ParameterObject @PageableDefault(size = 20)
                                                                      Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(maintenance.history(id, pageable)));
    }
}
