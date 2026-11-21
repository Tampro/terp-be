package com.terp.erp.document.contract;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
import com.terp.erp.document.common.EStatus;
import com.terp.erp.document.contract.ContractTypeService.Option;
import com.terp.erp.document.contract.ContractTypeService.StatusCounts;
import com.terp.erp.document.contract.ContractTypeService.TypeDto;
import com.terp.erp.document.contract.ContractTypeService.TypeForm;

import java.util.List;

import static com.terp.erp.document.common.Texts.COMPANY;

@Tag(name = "Contract types")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/document/contract-types")
@SecurityRequirement(name = "bearerAuth")
public class ContractTypeController {
    private static final String NOT_FOUND = "No such contract type in your company (DOC_005)";
    private static final String NO_COMPANY = "Super admin without companyId (DOC_001)";

    private final ContractTypeService service;

    public record StatusForm(@NotNull EStatus status) {
    }

    @Operation(summary = "Contract types of the company", description = "keyword = name or description; renewable = "
            + "the Renewable filter; status = the tab.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TypeDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean renewable,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, renewable, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)", description = "Same filters as the list.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean renewable) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, renewable));
    }

    @Operation(summary = "Contract type dropdown", description = "Active types, with the default duration.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Option>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Contract type details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<TypeDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add Contract Type")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name already used (DOC_003), " + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_MANAGE')")
    @PostMapping
    public ApiMessageDto<TypeDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                         @Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a contract type", description = "Full replacement; renewable and status omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name already used (DOC_003)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('CONTRACT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<TypeDto> update(@PathVariable Long id, @Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Toggle Status of a contract type")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<TypeDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a contract type", description = "Only a type without contracts.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "The type has contracts (DOC_004)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('CONTRACT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
