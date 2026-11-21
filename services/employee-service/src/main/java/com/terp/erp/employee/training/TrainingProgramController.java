package com.terp.erp.employee.training;

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
import com.terp.erp.employee.training.TrainingProgramService.ProgramDto;
import com.terp.erp.employee.training.TrainingProgramService.ProgramForm;
import com.terp.erp.employee.training.TrainingProgramService.StatusCounts;
import com.terp.erp.employee.training.TrainingSupport.Ref;

import java.util.List;

import static com.terp.erp.employee.training.TrainingSupport.COMPANY;

@Tag(name = "Training programs")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/training/programs")
@SecurityRequirement(name = "bearerAuth")
public class TrainingProgramController {
    private static final String NOT_FOUND = "No such training program in your company (EMP_081)";

    private final TrainingProgramService service;

    @Operation(summary = "Training programs of the company", description = "keyword matches the name or "
            + "description; typeId = the Training Type filter; status = the tab.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ProgramDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) TrainingProgram.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, typeId, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Draft, Active, Completed, Cancelled)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long typeId) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, typeId));
    }

    @Operation(summary = "Draft and active programs for dropdowns, by name")
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Ref>> options(@Parameter(description = COMPANY) @RequestParam(required = false)
                                            Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Program details", description = "With signed links to the materials.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ProgramDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a training program (status DRAFT unless given)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (EMP_011), type not in the company "
            + "(EMP_080), material not in the media library (EMP_012), super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PostMapping
    public ApiMessageDto<ProgramDto> create(@Parameter(description = COMPANY) @RequestParam(required = false)
                                            Long companyId, @Valid @RequestBody ProgramForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a training program", description = "Full replacement; status omitted = kept; "
            + "materialIds is the full set.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "EMP_011, EMP_080, EMP_012"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ProgramDto> update(@PathVariable Long id, @Valid @RequestBody ProgramForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a training program")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
