package com.terp.erp.iam.saas;

import io.swagger.v3.oas.annotations.Operation;
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
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.iam.saas.CurrencyService.CurrencyDto;
import com.terp.erp.iam.saas.CurrencyService.CurrencyForm;

import java.util.List;

@Tag(name = "SaaS — currencies")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/saas")
@SecurityRequirement(name = "bearerAuth")
public class CurrencyController {
    private final CurrencyService currencies;

    @Operation(summary = "Currencies for pickers (default first)",
            description = "Any company user who can see the subscription, or a super admin.")
    @PreAuthorize("@PermissionChecker.has('SAAS_SUBSCRIPTION_VIEW')")
    @GetMapping("/currencies")
    public ApiMessageDto<List<CurrencyDto>> all() {
        return ApiMessageDto.ok(currencies.all());
    }

    @Operation(summary = "Currency list (super admin)", description = "keyword matches name, code or symbol.")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/currencies")
    public ApiMessageDto<ResponseListDto<CurrencyDto>> list(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(currencies.search(keyword, pageable)));
    }

    @Operation(summary = "View a currency")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such currency (SAAS_022)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/admin/currencies/{id}")
    public ApiMessageDto<CurrencyDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(currencies.get(id));
    }

    @Operation(summary = "Add a currency", description = "defaultCurrency = true moves the default flag here; the "
            + "first currency ever created is the default.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Code taken (SAAS_023)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PostMapping("/admin/currencies")
    public ApiMessageDto<CurrencyDto> create(@Valid @RequestBody CurrencyForm form) {
        return ApiMessageDto.ok(currencies.create(form));
    }

    @Operation(summary = "Edit a currency (full replacement)", description = "Omitted symbol/description are cleared. "
            + "The default currency keeps the flag until another one is set as default.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Code taken (SAAS_023), un-defaulting the default (SAAS_025)"),
            @ApiResponse(responseCode = "404", description = "No such currency (SAAS_022)")})
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PutMapping("/admin/currencies/{id}")
    public ApiMessageDto<CurrencyDto> update(@PathVariable Long id, @Valid @RequestBody CurrencyForm form) {
        return ApiMessageDto.ok(currencies.update(id, form));
    }

    @Operation(summary = "Delete a currency (permanent)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Default currency (SAAS_024)"),
            @ApiResponse(responseCode = "404", description = "No such currency (SAAS_022)")})
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @DeleteMapping("/admin/currencies/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        currencies.delete(id);
        return ApiMessageDto.ok(null);
    }
}
