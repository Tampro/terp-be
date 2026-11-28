package com.terp.erp.member.customer;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.member.customer.AddressService.AddressDto;
import com.terp.erp.member.customer.AddressService.AddressForm;
import com.terp.erp.security.CustomerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The signed-in customer's delivery addresses in a shop (customer tokens only; own membership only). */
@Tag(name = "Shop membership (customer)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shop/{slug}/me/addresses")
@SecurityRequirement(name = "bearerAuth")
public class AddressController {
    private static final String MEMBER_404 = "Shop not available (MEM_001), not a member (MEM_004)";
    private static final String ADDRESS_404 = MEMBER_404 + ", no such address of yours (MEM_007)";
    private static final String FORM_400 = "Field out of its limits — phone 9-11 digits, postcode 5 digits, lat/lon range (COMMON_001)";

    private final AddressService service;

    @Operation(summary = "My delivery addresses", description = "Default first, then newest; each with the store serving it "
            + "(servingStore null when no store of the shop delivers there).")
    @ApiResponses(@ApiResponse(responseCode = "404", description = MEMBER_404))
    @GetMapping
    public ApiMessageDto<List<AddressDto>> list(@PathVariable String slug, @AuthenticationPrincipal CustomerPrincipal me) {
        return ApiMessageDto.ok(service.list(slug, me.sub()));
    }

    @Operation(summary = "Add a delivery address", description = "At most 10; the first one is default.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = FORM_400 + ", 11th address (MEM_008)"),
            @ApiResponse(responseCode = "404", description = MEMBER_404)})
    @PostMapping
    public ApiMessageDto<AddressDto> create(@PathVariable String slug, @AuthenticationPrincipal CustomerPrincipal me,
                                            @Valid @RequestBody AddressForm form) {
        return ApiMessageDto.ok(service.create(slug, me.sub(), form));
    }

    @Operation(summary = "Change a delivery address", description = "Full replacement; isDefault=true moves the default here.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = FORM_400),
            @ApiResponse(responseCode = "404", description = ADDRESS_404)})
    @PutMapping("/{id}")
    public ApiMessageDto<AddressDto> replace(@PathVariable String slug, @PathVariable Long id,
                                             @AuthenticationPrincipal CustomerPrincipal me, @Valid @RequestBody AddressForm form) {
        return ApiMessageDto.ok(service.replace(slug, me.sub(), id, form));
    }

    @Operation(summary = "Make this address the default")
    @ApiResponses(@ApiResponse(responseCode = "404", description = ADDRESS_404))
    @PutMapping("/{id}/default")
    public ApiMessageDto<AddressDto> makeDefault(@PathVariable String slug, @PathVariable Long id,
                                                 @AuthenticationPrincipal CustomerPrincipal me) {
        return ApiMessageDto.ok(service.makeDefault(slug, me.sub(), id));
    }

    @Operation(summary = "Delete a delivery address", description = "Hard delete; the newest remaining one becomes default.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = ADDRESS_404))
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable String slug, @PathVariable Long id,
                                      @AuthenticationPrincipal CustomerPrincipal me) {
        service.delete(slug, me.sub(), id);
        return ApiMessageDto.ok(null);
    }
}
