package com.terp.erp.project.task;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import com.terp.erp.project.project.ProjectEnums.EStageKind;
import com.terp.erp.project.task.StageService.RenameForm;
import com.terp.erp.project.task.StageService.StageDto;
import com.terp.erp.project.task.StageService.StageForm;
import com.terp.erp.project.task.StageService.StageOrderForm;

import java.util.List;

@Tag(name = "Projects — task and bug stages")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/stages")
@SecurityRequirement(name = "bearerAuth")
public class StageController {
    private static final String COMPANY = "Super admin only: the company to act on (required for a super admin)";
    private final StageService service;

    @Operation(summary = "Stages of a board in order (defaults created on first use); the last task stage is done")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "PRJ_001"))
    @PreAuthorize("@PermissionChecker.has('PROJECT_VIEW')")
    @GetMapping
    public ApiMessageDto<List<StageDto>> list(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                              @RequestParam EStageKind kind) {
        return ApiMessageDto.ok(service.list(companyId, kind));
    }

    @Operation(summary = "Add a stage at the end (a new last task stage becomes the done stage)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "PRJ_001, COMMON_001"))
    @PreAuthorize("@PermissionChecker.has('PROJECT_MANAGE')")
    @PostMapping
    public ApiMessageDto<StageDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                          @Valid @RequestBody StageForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Reorder the stages of a board")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Not exactly the stages of that kind (PRJ_013), PRJ_001"))
    @PreAuthorize("@PermissionChecker.has('PROJECT_MANAGE')")
    @PutMapping("/order")
    public ApiMessageDto<List<StageDto>> reorder(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                                 @Valid @RequestBody StageOrderForm form) {
        return ApiMessageDto.ok(service.reorder(companyId, form));
    }

    @Operation(summary = "Rename a stage")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "PRJ_012"))
    @PreAuthorize("@PermissionChecker.has('PROJECT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<StageDto> rename(@PathVariable Long id, @Valid @RequestBody RenameForm form) {
        return ApiMessageDto.ok(service.rename(id, form));
    }

    @Operation(summary = "Delete a stage")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Still used (PRJ_014), last stage (PRJ_015)"),
            @ApiResponse(responseCode = "404", description = "PRJ_012")})
    @PreAuthorize("@PermissionChecker.has('PROJECT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
