package com.terp.erp.asset.item;

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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.terp.erp.asset.item.Asset.ECondition;
import com.terp.erp.asset.item.Asset.EStatus;
import com.terp.erp.asset.item.AssetExcelService.ImportResult;
import com.terp.erp.asset.item.AssetService.AssetDto;
import com.terp.erp.asset.item.AssetService.AssetForm;
import com.terp.erp.asset.item.AssetService.Filter;
import com.terp.erp.asset.item.AssetService.StatusCounts;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static com.terp.erp.asset.common.Texts.COMPANY;

@Tag(name = "Assets")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/asset/assets")
@SecurityRequirement(name = "bearerAuth")
public class AssetController {
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final String KEYWORD = "Matches the name, asset code or serial number";
    private static final String NOT_FOUND = "No such asset in your company (AST_005)";
    private static final String FORM_ERRORS = "Asset type not in the company (AST_002), code already used (AST_006), "
            + "salvage value over the cost (AST_010), status not AVAILABLE/DISPOSED (AST_015), assigned or under "
            + "maintenance (AST_016), warranty expiry before purchase (AST_017), file not in the media library "
            + "(AST_009)";

    private final AssetService service;
    private final AssetExcelService excel;

    @Operation(summary = "Assets of the company", description = "Filters: assetTypeId, condition, purchase date "
            + "range; status = the tab (All, Available, Assigned, Maintenance, Disposed).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (AST_001)"))
    @PreAuthorize("@PermissionChecker.has('ASSET_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<AssetDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long assetTypeId,
            @RequestParam(required = false) ECondition condition,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseTo,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId,
                new Filter(assetTypeId, condition, purchaseFrom, purchaseTo, keyword), status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs", description = "Same filters as the list.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (AST_001)"))
    @PreAuthorize("@PermissionChecker.has('ASSET_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long assetTypeId,
            @RequestParam(required = false) ECondition condition,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseTo) {
        return ApiMessageDto.ok(service.statusCounts(companyId,
                new Filter(assetTypeId, condition, purchaseFrom, purchaseTo, keyword)));
    }

    @Operation(summary = "Export the assets (.xlsx)", description = "Same filters as the list, all pages.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (AST_001)"))
    @PreAuthorize("@PermissionChecker.has('ASSET_VIEW')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long assetTypeId,
            @RequestParam(required = false) ECondition condition,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseTo,
            @RequestParam(required = false) EStatus status) {
        return file(excel.export(companyId, new Filter(assetTypeId, condition, purchaseFrom, purchaseTo, keyword),
                status), "assets.xlsx");
    }

    @Operation(summary = "Import template (.xlsx)", description = "Asset Type must match an existing type name; "
            + "Status (AVAILABLE/DISPOSED), Condition and Depreciation Method use their codes.")
    @PreAuthorize("@PermissionChecker.has('ASSET_MANAGE')")
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        return file(excel.template(), "assets-import-template.xlsx");
    }

    @Operation(summary = "Import assets (.xlsx, at most 2000 rows, 5 MB)", description = "Rows with errors are "
            + "reported and skipped; the others are saved.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Not a workbook (AST_011), too many rows (AST_012), "
            + "file too large (AST_014), super admin without companyId (AST_001)"))
    @PreAuthorize("@PermissionChecker.has('ASSET_MANAGE')")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiMessageDto<ImportResult> importFile(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestPart("file") MultipartFile file) {
        return ApiMessageDto.ok(excel.importFile(companyId, file));
    }

    @Operation(summary = "Asset details", description = "With the image and documents (links from media-service; "
            + "a file you cannot see is left out).")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('ASSET_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<AssetDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add Asset", description = "Image and documents: upload them to the media library first, "
            + "then send their ids.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS + ", super admin without companyId "
                    + "(AST_001)"),
            @ApiResponse(responseCode = "503", description = "media-service unavailable (AST_008)")})
    @PreAuthorize("@PermissionChecker.has('ASSET_MANAGE')")
    @PostMapping
    public ApiMessageDto<AssetDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                          @Valid @RequestBody AssetForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit an asset", description = "Full replacement (documents = full set); status and "
            + "condition omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('ASSET_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<AssetDto> update(@PathVariable Long id, @Valid @RequestBody AssetForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete an asset", description = "Not while assigned or under maintenance.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Assigned or under maintenance (AST_016)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('ASSET_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name) {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
