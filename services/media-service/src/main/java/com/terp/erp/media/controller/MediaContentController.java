package com.terp.erp.media.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.exception.ApiException;
import com.terp.erp.media.constant.EMediaError;
import com.terp.erp.media.service.MediaService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

/** Serves bytes for a valid time-limited link — the only media path without a bearer token. */
@Tag(name = "Media library")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/media/files")
public class MediaContentController {
    private final MediaService media;

    @Operation(summary = "File content through a signed link (View / Download)",
            description = "Use the viewUrl / downloadUrl returned by the other endpoints; they stop working at linkExpiresAt.")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Link invalid or expired (MEDIA_007)"),
            @ApiResponse(responseCode = "404", description = "File deleted (MEDIA_001)")})
    @GetMapping("/{id}/content")
    public ResponseEntity<FileSystemResource> content(@PathVariable Long id, @RequestParam long exp,
                                                      @RequestParam(defaultValue = "0") int dl,
                                                      @RequestParam String sig) {
        MediaService.Content c = media.content(id, exp, dl, sig);
        if (!Files.isRegularFile(c.path())) {
            throw ApiException.notFound(EMediaError.MEDIA_001);
        }
        ContentDisposition disposition = (c.download() ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(c.downloadName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // The stored type is the only type: the browser must not guess another one (e.g. HTML in a .txt).
                // (No CSP sandbox: it stops the browser's PDF viewer. Script-capable types are not accepted at upload.)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .body(new FileSystemResource(c.path()));
    }
}
