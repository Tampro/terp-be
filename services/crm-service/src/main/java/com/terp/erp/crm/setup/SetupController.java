package com.terp.erp.crm.setup;

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
import com.terp.erp.crm.setup.SetupService.LabelDto;
import com.terp.erp.crm.setup.SetupService.LabelForm;
import com.terp.erp.crm.setup.SetupService.NameForm;
import com.terp.erp.crm.setup.SetupService.PipelineDto;
import com.terp.erp.crm.setup.SetupService.SourceDto;
import com.terp.erp.crm.setup.SetupService.StageDto;
import com.terp.erp.crm.setup.SetupService.StageForm;
import com.terp.erp.crm.setup.SetupService.StageOrderForm;

import java.util.List;

@Tag(name = "CRM — setup")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/crm")
@SecurityRequirement(name = "bearerAuth")
public class SetupController {
    private static final String COMPANY = "Super admin only: the company to act on (required for a super admin)";
    private final SetupService service;

    @Operation(summary = "Pipelines with their lead and deal stages, in board order")
    @PreAuthorize("@PermissionChecker.has('CRM_VIEW')")
    @GetMapping("/pipelines")
    public ApiMessageDto<List<PipelineDto>> pipelines(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.pipelines(companyId));
    }

    @Operation(summary = "Create a pipeline")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name already used (CRM_019), validation (COMMON_001)"))
    @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
    @PostMapping("/pipelines")
    public ApiMessageDto<PipelineDto> createPipeline(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                                     @Valid @RequestBody NameForm form) {
        return ApiMessageDto.ok(service.createPipeline(companyId, form));
    }

    @Operation(summary = "Rename a pipeline")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "CRM_002"))
    @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
    @PutMapping("/pipelines/{id}")
    public ApiMessageDto<PipelineDto> renamePipeline(@PathVariable Long id, @Valid @RequestBody NameForm form) {
        return ApiMessageDto.ok(service.renamePipeline(id, form));
    }

    @Operation(summary = "Delete a pipeline (with its stages and labels)")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Still has leads or deals (CRM_009)"),
            @ApiResponse(responseCode = "404", description = "CRM_002")})
    @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
    @DeleteMapping("/pipelines/{id}")
    public ApiMessageDto<Void> deletePipeline(@PathVariable Long id) {
        service.deletePipeline(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Add a lead or deal stage at the end of the board")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "CRM_002"))
    @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
    @PostMapping("/pipelines/{id}/stages")
    public ApiMessageDto<StageDto> addStage(@PathVariable Long id, @Valid @RequestBody StageForm form) {
        return ApiMessageDto.ok(service.addStage(id, form));
    }

    @Operation(summary = "Reorder the lead or deal stages (drag and drop)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Not exactly the stages of that kind (CRM_020)"))
    @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
    @PutMapping("/pipelines/{id}/stages/order")
    public ApiMessageDto<PipelineDto> reorder(@PathVariable Long id, @Valid @RequestBody StageOrderForm form) {
        return ApiMessageDto.ok(service.reorder(id, form));
    }

    @Operation(summary = "Rename a stage")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "CRM_003"))
    @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
    @PutMapping("/stages/{id}")
    public ApiMessageDto<StageDto> renameStage(@PathVariable Long id, @Valid @RequestBody NameForm form) {
        return ApiMessageDto.ok(service.renameStage(id, form));
    }

    @Operation(summary = "Delete a stage")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Still has leads or deals (CRM_009)"),
            @ApiResponse(responseCode = "404", description = "CRM_003")})
    @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
    @DeleteMapping("/stages/{id}")
    public ApiMessageDto<Void> deleteStage(@PathVariable Long id) {
        service.deleteStage(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Labels", description = "With pipelineId: that pipeline's labels; without: the company's.")
    @PreAuthorize("@PermissionChecker.has('CRM_VIEW')")
    @GetMapping("/labels")
    public ApiMessageDto<List<LabelDto>> labels(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                                @RequestParam(required = false) Long pipelineId) {
        return ApiMessageDto.ok(service.labels(companyId, pipelineId));
    }

    @Operation(summary = "Create a label")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Pipeline (CRM_002)"))
    @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
    @PostMapping("/labels")
    public ApiMessageDto<LabelDto> createLabel(@Valid @RequestBody LabelForm form) {
        return ApiMessageDto.ok(service.saveLabel(null, form));
    }

    @Operation(summary = "Edit a label", description = "Full replacement.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "CRM_004, CRM_002"))
    @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
    @PutMapping("/labels/{id}")
    public ApiMessageDto<LabelDto> updateLabel(@PathVariable Long id, @Valid @RequestBody LabelForm form) {
        return ApiMessageDto.ok(service.saveLabel(id, form));
    }

    @Operation(summary = "Delete a label")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "In use (CRM_009)"),
            @ApiResponse(responseCode = "404", description = "CRM_004")})
    @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
    @DeleteMapping("/labels/{id}")
    public ApiMessageDto<Void> deleteLabel(@PathVariable Long id) {
        service.deleteLabel(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Sources")
    @PreAuthorize("@PermissionChecker.has('CRM_VIEW')")
    @GetMapping("/sources")
    public ApiMessageDto<List<SourceDto>> sources(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.sources(companyId));
    }

    @Operation(summary = "Create a source")
    @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
    @PostMapping("/sources")
    public ApiMessageDto<SourceDto> createSource(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                                 @Valid @RequestBody NameForm form) {
        return ApiMessageDto.ok(service.saveSource(companyId, null, form));
    }

    @Operation(summary = "Rename a source")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "CRM_005"))
    @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
    @PutMapping("/sources/{id}")
    public ApiMessageDto<SourceDto> updateSource(@PathVariable Long id, @Valid @RequestBody NameForm form) {
        return ApiMessageDto.ok(service.saveSource(null, id, form));
    }

    @Operation(summary = "Delete a source")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "In use (CRM_009)"),
            @ApiResponse(responseCode = "404", description = "CRM_005")})
    @PreAuthorize("@PermissionChecker.has('CRM_MANAGE')")
    @DeleteMapping("/sources/{id}")
    public ApiMessageDto<Void> deleteSource(@PathVariable Long id) {
        service.deleteSource(id);
        return ApiMessageDto.ok(null);
    }
}
