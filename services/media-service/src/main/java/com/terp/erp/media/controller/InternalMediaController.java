package com.terp.erp.media.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.media.service.MediaDtos.MediaFileDto;
import com.terp.erp.media.service.MediaService;

/**
 * Service-to-service only: accepted for the service-account token of erp.media.internal-client-id (SecurityConfig),
 * not routed by the gateway. The calling service authorizes its own user before asking.
 */
@Tag(name = "Internal")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/media")
@SecurityRequirement(name = "bearerAuth")
public class InternalMediaController {
    private final MediaService media;

    @Operation(summary = "File with signed view/download links, for another ERP service",
            description = "companyId is required: the file must belong to it.")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Token not of the allowed service account (COMMON_004)"),
            @ApiResponse(responseCode = "404", description = "No such file in that company (MEDIA_001)")})
    @GetMapping("/files/{id}")
    public ApiMessageDto<MediaFileDto> file(@PathVariable Long id, @RequestParam Long companyId) {
        return ApiMessageDto.ok(media.internalFile(id, companyId));
    }
}
