package com.terp.erp.asset.depreciation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.asset.depreciation.AssetDashboardService.Dashboard;
import com.terp.erp.asset.depreciation.DepreciationMath.Result;
import com.terp.erp.asset.depreciation.DepreciationService.Report;
import com.terp.erp.common.ApiMessageDto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static com.terp.erp.asset.common.Texts.COMPANY;

@Tag(name = "Asset depreciation and dashboard")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/asset")
@SecurityRequirement(name = "bearerAuth")
public class DepreciationController {
    private static final String METHOD = "Straight line: (cost − salvage) / life months per month. Declining balance: "
            + "2 / life years of the value at the start of each year, spread over its months, never below salvage. "
            + "Whole months since the purchase date; no purchase date or no useful life = no depreciation.";

    private final DepreciationService depreciation;
    private final AssetDashboardService dashboard;

    @Operation(summary = "Depreciation tab of an asset", description = METHOD + " Computed for today.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such asset in your company (AST_005)"))
    @PreAuthorize("@PermissionChecker.has('ASSET_VIEW')")
    @GetMapping("/assets/{id}/depreciation")
    public ApiMessageDto<Result> ofAsset(@PathVariable Long id) {
        return ApiMessageDto.ok(depreciation.ofAsset(id));
    }

    @Operation(summary = "Asset Depreciation Report", description = "summary = the three cards over every matching "
            + "asset; details = the table, by asset name. " + METHOD)
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (AST_001)"))
    @PreAuthorize("@PermissionChecker.has('ASSET_VIEW')")
    @GetMapping("/depreciation")
    public ApiMessageDto<Report> report(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long assetTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseTo,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiMessageDto.ok(depreciation.report(companyId, assetTypeId, purchaseFrom, purchaseTo, pageable));
    }

    @Operation(summary = "Export the depreciation report (CSV)", description = "Same filters, every row.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (AST_001)"))
    @PreAuthorize("@PermissionChecker.has('ASSET_VIEW')")
    @GetMapping("/depreciation/export")
    public ResponseEntity<byte[]> export(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long assetTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseTo) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("asset-depreciation.csv", StandardCharsets.UTF_8).build().toString())
                .body(depreciation.csv(companyId, assetTypeId, purchaseFrom, purchaseTo));
    }

    @Operation(summary = "Asset Dashboard", description = "Cards (total, available, assigned, under maintenance with "
            + "percentages of the total), value summary, distribution by type, the 5 latest assignments, the next 5 "
            + "maintenance records (scheduled or in progress, starting today or later) and warranties expiring "
            + "within 30 days.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (AST_001)"))
    @PreAuthorize("@PermissionChecker.has('ASSET_VIEW')")
    @GetMapping("/dashboard")
    public ApiMessageDto<Dashboard> dashboard(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(dashboard.dashboard(companyId));
    }
}
