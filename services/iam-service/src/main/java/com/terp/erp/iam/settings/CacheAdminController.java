package com.terp.erp.iam.settings;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.settings.CacheAdminService.CacheView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Settings")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/admin/cache")
@SecurityRequirement(name = "bearerAuth")
public class CacheAdminController {
    private final CacheAdminService service;

    @Operation(summary = "IAM caches and their size", description = "Name, description, estimated number of "
            + "entries and expiry of each in-memory cache of IAM. Other services' caches are not listed (they expire "
            + "within a minute). No Optimize: it has no equivalent on this platform.")
    @ApiResponses(@ApiResponse(responseCode = "403", description = "Not a super admin (COMMON_004)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping
    public ApiMessageDto<CacheView> cache() {
        return ApiMessageDto.ok(service.view());
    }

    @Operation(summary = "Clear Cache", description = "Empties every listed cache (permissions are resolved again "
            + "on the next request); answers the sizes after clearing. Logged-out sessions and login rate limits are "
            + "never cleared.")
    @ApiResponses(@ApiResponse(responseCode = "403", description = "Not a super admin (COMMON_004)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PostMapping("/clear")
    public ApiMessageDto<CacheView> clear() {
        return ApiMessageDto.ok(service.clear());
    }
}
