package com.terp.erp.asset.type;

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
import com.terp.erp.asset.type.AssetTypeService.Option;
import com.terp.erp.asset.type.AssetTypeService.TypeDto;
import com.terp.erp.asset.type.AssetTypeService.TypeForm;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;

import java.util.List;

import static com.terp.erp.asset.common.Texts.COMPANY;

@Tag(name = "Asset types")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/asset/types")
@SecurityRequirement(name = "bearerAuth")
public class AssetTypeController {
    private static final String NOT_FOUND = "No such asset type in your company (AST_002)";

    private final AssetTypeService service;

    @Operation(summary = "Asset types of the company", description = "keyword = the name; assets = number of assets "
            + "of the type.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (AST_001)"))
    @PreAuthorize("@PermissionChecker.has('ASSET_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TypeDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, keyword, pageable)));
    }

    @Operation(summary = "Asset type dropdown", description = "All types of the company by name.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (AST_001)"))
    @PreAuthorize("@PermissionChecker.has('ASSET_VIEW')")
    @GetMapping("/options")
    public ApiMessageDto<List<Option>> options(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.options(companyId));
    }

    @Operation(summary = "Asset type details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('ASSET_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<TypeDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add New Asset Type")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name already used (AST_003), super admin "
            + "without companyId (AST_001)"))
    @PreAuthorize("@PermissionChecker.has('ASSET_MANAGE')")
    @PostMapping
    public ApiMessageDto<TypeDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                         @Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit an asset type", description = "Full replacement.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name already used (AST_003)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('ASSET_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<TypeDto> update(@PathVariable Long id, @Valid @RequestBody TypeForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete an asset type", description = "Only a type without assets.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "The type still has assets (AST_004)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('ASSET_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
