package com.terp.erp.store.store;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.store.store.StoreService.ResolveDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

import static com.terp.erp.store.common.Texts.COMPANY;

@Tag(name = "Stores (warehouse, order hours, delivery fees)")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/store/resolve")
@SecurityRequirement(name = "bearerAuth")
public class ResolveController {
    private final StoreService service;

    @Operation(summary = "Which store delivers to this point?", description = "Candidates: live, active stores of the "
            + "company with an active zone containing the point (an edge counts as inside). Several: the store nearest "
            + "to the point by its own coordinates (stores without coordinates last, then the smaller id). A store closed "
            + "by the hour or on a day-off is still returned, with orderableNow=false and opensAt.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "lat/lon missing or out of range (COMMON_001), "
            + "super admin without companyId (STO_007)"),
            @ApiResponse(responseCode = "404", description = "No store delivers to this point (STO_010)")})
    @PreAuthorize("@PermissionChecker.has('STORE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResolveDto> resolve(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(example = "37.4800") @RequestParam @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            @Parameter(example = "126.8900") @RequestParam @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal lon) {
        return ApiMessageDto.ok(service.resolve(companyId, lat, lon));
    }
}
