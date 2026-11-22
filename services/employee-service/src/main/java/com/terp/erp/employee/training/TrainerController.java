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
import com.terp.erp.employee.training.TrainerService.TrainerDto;
import com.terp.erp.employee.training.TrainerService.TrainerForm;
import com.terp.erp.employee.training.TrainingSupport.Ref;

import java.util.List;

import static com.terp.erp.employee.training.TrainingSupport.COMPANY;

@Tag(name = "Trainers")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/training/trainers")
@SecurityRequirement(name = "bearerAuth")
public class TrainerController {
    private static final String NOT_FOUND = "No such trainer in your company (EMP_100)";

    private final TrainerService service;

    @Operation(summary = "Trainers of the company", description = "keyword matches the name, email, expertise or "
            + "qualification; branchId / departmentId / active = the filters.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TrainerDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, branchId, departmentId, active,
                pageable)));
    }

    @Operation(summary = "Active trainers for dropdowns, by name")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Ref>> options(@Parameter(description = COMPANY) @RequestParam(required = false)
                                            Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Trainer details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<TrainerDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a trainer")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Validation (COMMON_001), branch not in the "
            + "company (EMP_002), department not of the branch or no branch given (EMP_003), super admin without "
            + "companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PostMapping
    public ApiMessageDto<TrainerDto> create(@Parameter(description = COMPANY) @RequestParam(required = false)
                                            Long companyId, @Valid @RequestBody TrainerForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a trainer", description = "Full replacement; active omitted = true.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "COMMON_001, EMP_002, EMP_003"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<TrainerDto> update(@PathVariable Long id, @Valid @RequestBody TrainerForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a trainer", description = "Soft delete; not while a session names the trainer.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Named on a session (EMP_010)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
