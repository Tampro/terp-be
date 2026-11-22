package com.terp.erp.support.portal;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.support.portal.PortalSettingsService.PageDto;
import com.terp.erp.support.portal.PortalSettingsService.PageForm;
import com.terp.erp.support.portal.PortalSettingsService.PageRow;
import com.terp.erp.support.portal.PortalSettingsService.SettingsDto;
import com.terp.erp.support.portal.PortalSettingsService.SettingsForm;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support/portal")
@Tag(name = "Support portal settings", description = "Slug, branding, sections, quick links, custom pages (ERP manual 18)")
@SecurityRequirement(name = "bearerAuth")
public class PortalSettingsController {
    private final PortalSettingsService service;

    @GetMapping("/settings")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_SETTINGS_MANAGE')")
    @Operation(summary = "Portal settings of the company (data null until saved once)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_001")})
    public ApiMessageDto<SettingsDto> get(@RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.get(companyId));
    }

    @PutMapping("/settings")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_SETTINGS_MANAGE')")
    @Operation(summary = "Save the portal settings", description = "Full replacement: slug, enabled, branding and the "
            + "whole content (sections, support information, contact information, quick links).")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_001, SUP_011 (slug), SUP_012 (taken)")})
    public ApiMessageDto<SettingsDto> save(@RequestParam(required = false) Long companyId,
                                           @Valid @RequestBody SettingsForm form) {
        return ApiMessageDto.ok(service.save(companyId, form));
    }

    @GetMapping("/pages")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_SETTINGS_MANAGE')")
    @Operation(summary = "Custom pages of the portal")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SUP_001")})
    public ApiMessageDto<List<PageRow>> pages(@RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.pages(companyId));
    }

    @GetMapping("/pages/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_SETTINGS_MANAGE')")
    @Operation(summary = "A custom page with its contents")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_026")})
    public ApiMessageDto<PageDto> page(@PathVariable Long id) {
        return ApiMessageDto.ok(service.page(id));
    }

    @PostMapping("/pages")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_SETTINGS_MANAGE')")
    @Operation(summary = "Create a custom page")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_001, SUP_011, SUP_027, SUP_034")})
    public ApiMessageDto<PageDto> createPage(@RequestParam(required = false) Long companyId,
                                             @Valid @RequestBody PageForm form) {
        return ApiMessageDto.ok(service.createPage(companyId, form));
    }

    @PutMapping("/pages/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_SETTINGS_MANAGE')")
    @Operation(summary = "Replace a custom page (full replacement)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, SUP_011, SUP_027, SUP_034"),
            @ApiResponse(responseCode = "404", description = "SUP_026")})
    public ApiMessageDto<PageDto> updatePage(@PathVariable Long id, @Valid @RequestBody PageForm form) {
        return ApiMessageDto.ok(service.updatePage(id, form));
    }

    @DeleteMapping("/pages/{id}")
    @PreAuthorize("@PermissionChecker.has('SUPPORT_SETTINGS_MANAGE')")
    @Operation(summary = "Delete a custom page (soft)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUP_026")})
    public ApiMessageDto<Void> deletePage(@PathVariable Long id) {
        service.deletePage(id);
        return ApiMessageDto.ok(null);
    }
}
