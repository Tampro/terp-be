package com.terp.erp.store.zone;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.store.zone.StoreZoneService.ZoneDto;
import com.terp.erp.store.zone.StoreZoneService.ZoneForm;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Store delivery zones (GeoJSON Polygon)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/store/stores/{storeId}/zones")
@SecurityRequirement(name = "bearerAuth")
public class StoreZoneController {
    private static final String STORE_404 = "No such store in your company (STO_001)";
    private static final String ZONE_404 = "No such store in your company (STO_001) or no such zone in this store (STO_008)";
    private static final String AREA_400 = "Area not a one-ring GeoJSON Polygon of 3-500 points, lat/lon out of range "
            + "or self-intersecting (STO_009); name blank or over 100 characters (COMMON_001)";

    private final StoreZoneService service;

    @Operation(summary = "Zones of a store", description = "Active and inactive, ordered by id, not paged.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = STORE_404))
    @PreAuthorize("@PermissionChecker.has('STORE_VIEW')")
    @GetMapping
    public ApiMessageDto<List<ZoneDto>> list(@PathVariable Long storeId) {
        return ApiMessageDto.ok(service.list(storeId));
    }

    @Operation(summary = "Add a zone", description = "An open ring is closed by the server; active defaults to true.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = AREA_400),
            @ApiResponse(responseCode = "404", description = STORE_404)})
    @PreAuthorize("@PermissionChecker.has('STORE_MANAGE')")
    @PostMapping
    public ApiMessageDto<ZoneDto> create(@PathVariable Long storeId, @Valid @RequestBody ZoneForm form) {
        return ApiMessageDto.ok(service.create(storeId, form));
    }

    @Operation(summary = "Change a zone", description = "Full replacement of name, area and active.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = AREA_400),
            @ApiResponse(responseCode = "404", description = ZONE_404)})
    @PreAuthorize("@PermissionChecker.has('STORE_MANAGE')")
    @PutMapping("/{zoneId}")
    public ApiMessageDto<ZoneDto> update(@PathVariable Long storeId, @PathVariable Long zoneId,
                                         @Valid @RequestBody ZoneForm form) {
        return ApiMessageDto.ok(service.update(storeId, zoneId, form));
    }

    @Operation(summary = "Delete a zone", description = "Hard delete.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = ZONE_404))
    @PreAuthorize("@PermissionChecker.has('STORE_MANAGE')")
    @DeleteMapping("/{zoneId}")
    public ApiMessageDto<Void> delete(@PathVariable Long storeId, @PathVariable Long zoneId) {
        service.delete(storeId, zoneId);
        return ApiMessageDto.ok(null);
    }
}
