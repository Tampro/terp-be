package com.terp.erp.store.shop;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.store.shop.ShopSettingService.ShopSettingDto;
import com.terp.erp.store.shop.ShopSettingService.ShopSettingForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.terp.erp.store.common.Texts.COMPANY;

@Tag(name = "Shop setting (public address, on/off)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/store/shop-setting")
@SecurityRequirement(name = "bearerAuth")
public class ShopSettingController {
    private final ShopSettingService service;

    @Operation(summary = "The company's shop setting", description = "slug null and enabled false until it is saved.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (STO_007)"))
    @PreAuthorize("@PermissionChecker.has('STORE_VIEW')")
    @GetMapping
    public ApiMessageDto<ShopSettingDto> get(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.get(companyId));
    }

    @Operation(summary = "Save the shop setting", description = "Full replacement of slug and enabled; the slug is "
            + "trimmed and lower-cased, unique across companies, and can be changed at any time.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Slug used by another company (STO_014), reserved "
            + "slug (STO_015), slug not a-z/0-9/'-' of 3-40 or enabled missing (COMMON_001), super admin without "
            + "companyId (STO_007)"))
    @PreAuthorize("@PermissionChecker.has('STORE_MANAGE')")
    @PutMapping
    public ApiMessageDto<ShopSettingDto> put(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                             @Valid @RequestBody ShopSettingForm form) {
        return ApiMessageDto.ok(service.put(companyId, form));
    }
}
