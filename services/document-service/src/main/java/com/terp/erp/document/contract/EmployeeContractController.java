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
import com.terp.erp.document.contract.EmployeeContractService.Cards;
import com.terp.erp.document.contract.EmployeeContractService.ContractDto;
import com.terp.erp.document.contract.EmployeeContractService.ContractForm;
import com.terp.erp.document.contract.EmployeeContractService.Filter;
import com.terp.erp.document.contract.EmployeeContractService.StatusCounts;

import static com.terp.erp.document.common.Texts.COMPANY;

@Tag(name = "Employee contracts")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/document/contracts")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeContractController {
    private static final String KEYWORD = "Matches the contract number or the employee's name, no. or email";
    private static final String NOT_FOUND = "No such contract in your company (DOC_017)";
    private static final String NO_COMPANY = "Super admin without companyId (DOC_001)";
    private static final String FORM_ERRORS = "Type not in the company (DOC_005), inactive type (DOC_015), end before "
            + "start (DOC_018), not an employee of the company (DOC_013)";

    private final EmployeeContractService service;

    public record StatusForm(@NotNull EmployeeContract.EStatus status) {
    }

    @Operation(summary = "Employee contracts of the company", description = "Filters: contractTypeId, employeeId; "
            + "status = the tab (effective: an ACTIVE contract past its end reads EXPIRED). Each row has the "
            + "duration and progressPercent of the term. Needs EMPLOYEE_VIEW too (photos).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = NO_COMPANY),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (DOC_006)")})
    @PreAuthorize("@PermissionChecker.has('CONTRACT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ContractDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long contractTypeId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) EmployeeContract.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId,
                new Filter(keyword, contractTypeId, employeeId), status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs", description = "Effective statuses; same filters as the list.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long contractTypeId,
            @RequestParam(required = false) Long employeeId) {
        return ApiMessageDto.ok(service.statusCounts(companyId, new Filter(keyword, contractTypeId, employeeId)));
    }

    @Operation(summary = "Summary cards", description = "Active Contract, Contract Near Expire (active, ending within "
            + "30 days), Draft Contract.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_VIEW')")
    @GetMapping("/cards")
    public ApiMessageDto<Cards> cards(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.cards(companyId));
    }

    @Operation(summary = "Contract details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ContractDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add Employee Contract", description = "Created DRAFT with a number CT-yyyy-000123; end date "
            + "omitted = start + the type's default duration. The employee is checked in employee-service as you.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS + ", " + NO_COMPANY),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (DOC_006)")})
    @PreAuthorize("@PermissionChecker.has('CONTRACT_MANAGE')")
    @PostMapping
    public ApiMessageDto<ContractDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                             @Valid @RequestBody ContractForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a contract", description = "Full replacement of the details, only while DRAFT or "
            + "PENDING; number and status kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS + ", not editable in its status (DOC_027)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('CONTRACT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ContractDto> update(@PathVariable Long id, @Valid @RequestBody ContractForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Update Contract Status", description = "Allowed: DRAFT -> PENDING | ACTIVE; PENDING (Pending "
            + "Approval) -> DRAFT | ACTIVE; ACTIVE -> EXPIRED | TERMINATED | RENEWED; EXPIRED -> RENEWED; TERMINATED "
            + "and RENEWED are final.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Move not allowed (DOC_028)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('CONTRACT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<ContractDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a contract", description = "Only while DRAFT or PENDING.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not deletable in its status (DOC_027)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('CONTRACT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
