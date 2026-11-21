package com.terp.erp.recruitment.onboarding;

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
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.onboarding.ChecklistService.ItemDto;
import com.terp.erp.recruitment.onboarding.ChecklistService.ItemForm;
import com.terp.erp.recruitment.onboarding.ChecklistService.StatusForm;

@Tag(name = "Checklist Items")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/checklist-items")
@SecurityRequirement(name = "bearerAuth")
public class ChecklistItemController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";

    private final ChecklistService service;

    @Operation(summary = "Checklist items of the company", description = "Filters: Category, Checklist, Required, "
            + "status; keyword matches the task name.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ItemDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long checklistId,
            @RequestParam(required = false) ChecklistItem.ECategory category,
            @RequestParam(required = false) Boolean required,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = {"checklistId", "dueDay", "id"}) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.searchItems(companyId, keyword, checklistId, category, required, status, pageable)));
    }

    @Operation(summary = "Item details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such item in your company (REC_039)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ItemDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.getItem(id));
    }

    @Operation(summary = "Add an item to a checklist")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such checklist in your company (REC_038)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<ItemDto> create(@Valid @RequestBody ItemForm form) {
        return ApiMessageDto.ok(service.createItem(form));
    }

    @Operation(summary = "Edit an item", description = "Full replacement (may move it to another checklist); status "
            + "omitted = kept.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such item (REC_039) or checklist (REC_038)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ItemDto> update(@PathVariable Long id, @Valid @RequestBody ItemForm form) {
        return ApiMessageDto.ok(service.updateItem(id, form));
    }

    @Operation(summary = "Toggle Status (Active / Inactive)")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such item in your company (REC_039)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<ItemDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeItemStatus(id, form.status()));
    }

    @Operation(summary = "Delete an item")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such item in your company (REC_039)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.deleteItem(id);
        return ApiMessageDto.ok(null);
    }
}
