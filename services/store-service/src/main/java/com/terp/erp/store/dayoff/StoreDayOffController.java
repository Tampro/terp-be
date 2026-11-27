package com.terp.erp.store.dayoff;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.store.dayoff.StoreDayOffService.DayOffDto;
import com.terp.erp.store.dayoff.StoreDayOffService.DayOffForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Store day-offs")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/store/stores/{storeId}/day-offs")
@SecurityRequirement(name = "bearerAuth")
public class StoreDayOffController {
    private static final String STORE_404 = "No such store in your company (STO_001)";

    private final StoreDayOffService service;

    @Operation(summary = "Day-offs of a store", description = "Ordered by date, not paged. from defaults to today in "
            + "the store's time zone; to is inclusive and optional.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = STORE_404))
    @PreAuthorize("@PermissionChecker.has('STORE_VIEW')")
    @GetMapping
    public ApiMessageDto<List<DayOffDto>> list(@PathVariable Long storeId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.list(storeId, from, to));
    }

    @Operation(summary = "Add a day-off", description = "A whole local date of the store: no order that day; "
            + "orderable / resolve then answer orderableNow=false with the next opening after the day-offs.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Already a day-off (STO_011), date in the past in "
            + "the store's time zone (STO_012), date missing or reason over 255 characters (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = STORE_404)})
    @PreAuthorize("@PermissionChecker.has('STORE_MANAGE')")
    @PostMapping
    public ApiMessageDto<DayOffDto> create(@PathVariable Long storeId, @Valid @RequestBody DayOffForm form) {
        return ApiMessageDto.ok(service.create(storeId, form));
    }

    @Operation(summary = "Delete a day-off", description = "Hard delete.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such store in your company (STO_001) or no such "
            + "day-off in this store (STO_013)"))
    @PreAuthorize("@PermissionChecker.has('STORE_MANAGE')")
    @DeleteMapping("/{dayOffId}")
    public ApiMessageDto<Void> delete(@PathVariable Long storeId, @PathVariable Long dayOffId) {
        service.delete(storeId, dayOffId);
        return ApiMessageDto.ok(null);
    }
}
