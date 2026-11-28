package com.terp.erp.order.mileage;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.order.client.CartSourcesClient;
import com.terp.erp.order.mileage.MileageDtos.AccountDto;
import com.terp.erp.security.CustomerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/** The signed-in member's points in this shop (customer tokens only). */
@Tag(name = "Mileage (customer)")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CustomerMileageController {
    private final MileageService service;
    private final CartSourcesClient sources;

    @Operation(summary = "My points", description = "Balance, points expiring within 30 days, statement (newest first).")
    @ApiResponses({@ApiResponse(responseCode = "404", description = "Not a member (ORD_001)"),
            @ApiResponse(responseCode = "503", description = "member-service unavailable (ORD_007)")})
    @GetMapping("/api/v1/shop/{slug}/mileage")
    public ApiMessageDto<AccountDto> mine(@PathVariable String slug, @AuthenticationPrincipal CustomerPrincipal me,
                                          @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        CartSourcesClient.Membership m = sources.membership(slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT), me.sub());
        return ApiMessageDto.ok(service.statement(m.companyId(), m.customerId(), pageable));
    }
}
