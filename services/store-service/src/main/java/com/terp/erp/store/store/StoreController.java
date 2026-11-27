package com.terp.erp.store.store;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.store.store.StoreService.OrderableDto;
import com.terp.erp.store.store.StoreService.StoreDto;
import com.terp.erp.store.store.StoreService.StoreForm;
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

import java.time.Instant;

import static com.terp.erp.store.common.Texts.COMPANY;

@Tag(name = "Stores (warehouse, order hours, delivery fees)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/store/stores")
@SecurityRequirement(name = "bearerAuth")
public class StoreController {
    private static final String NOT_FOUND = "No such store in your company (STO_001)";
    private static final String FORM_ERRORS = "Code already used (STO_002), warehouse already linked to another store "
            + "(STO_003), warehouse not of the company or inactive (STO_004), order hours or time zone invalid (STO_005), "
            + "invalid field (COMMON_001); inventory's own error relayed when the caller may not read warehouses";

    private final StoreService service;

    @Operation(summary = "Stores of the company", description = "keyword = code or name; active filter optional.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (STO_007)"))
    @PreAuthorize("@PermissionChecker.has('STORE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<StoreDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, active, pageable)));
    }

    @Operation(summary = "Store details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('STORE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<StoreDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Can the store take an order at this instant?", description = "Window [orderStart, orderEnd) "
            + "in the store's time zone; at = UTC instant, default now. opensAt = next opening (UTC) when closed by the "
            + "hour, null when open or inactive. Day-offs are not applied yet.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('STORE_VIEW')")
    @GetMapping("/{id}/orderable")
    public ApiMessageDto<OrderableDto> orderable(@PathVariable Long id,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        return ApiMessageDto.ok(service.orderable(id, at));
    }

    @Operation(summary = "Add a store", description = "The warehouse is checked with inventory-service as the caller "
            + "(the caller also needs INVENTORY_VIEW). Fees default to 0, time zone to Asia/Seoul, active to true.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = FORM_ERRORS + ", super admin without companyId (STO_007)"),
            @ApiResponse(responseCode = "503", description = "inventory-service unavailable (STO_006)")})
    @PreAuthorize("@PermissionChecker.has('STORE_MANAGE')")
    @PostMapping
    public ApiMessageDto<StoreDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                          @Valid @RequestBody StoreForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Change a store", description = "Full replacement of every field; omitted fees become 0. "
            + "A changed warehouse is checked again.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = FORM_ERRORS),
            @ApiResponse(responseCode = "404", description = NOT_FOUND),
            @ApiResponse(responseCode = "503", description = "inventory-service unavailable (STO_006)")})
    @PreAuthorize("@PermissionChecker.has('STORE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<StoreDto> update(@PathVariable Long id, @Valid @RequestBody StoreForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a store", description = "Soft delete; its code and warehouse can be used again.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('STORE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
