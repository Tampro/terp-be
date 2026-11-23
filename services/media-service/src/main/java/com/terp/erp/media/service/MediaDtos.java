package com.terp.erp.media.service;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.terp.erp.media.constant.EMediaKind;
import com.terp.erp.media.model.MediaFile;
import com.terp.erp.media.model.MediaFolder;
import com.terp.erp.media.storage.LinkSigner.SignedLink;

import java.time.Instant;

public final class MediaDtos {
    private MediaDtos() {
    }

    static String str(Long id) {
        return id == null ? null : id.toString();
    }

    public record FolderDto(String id, String companyId, String name, long fileCount, Instant createdDate) {
        static FolderDto of(MediaFolder f, long count) {
            return new FolderDto(str(f.getId()), str(f.getCompanyId()), f.getName(), count, f.getCreatedDate());
        }
    }

    @Schema(description = "A file. viewUrl / downloadUrl are time-limited links (no token needed) valid until linkExpiresAt (epoch seconds).")
    public record MediaFileDto(String id, String companyId, String folderId, String originalName, String displayName,
                               String mimeType, String extension, EMediaKind kind, long sizeBytes, Instant uploadedAt,
                               String viewUrl, String downloadUrl, long linkExpiresAt) {
        static MediaFileDto of(MediaFile m, SignedLink view, SignedLink download) {
            return new MediaFileDto(str(m.getId()), str(m.getCompanyId()), str(m.getFolderId()), m.getOriginalName(),
                    m.getDisplayName(), m.getMimeType(), m.getExtension(), m.getKind(), m.getSizeBytes(),
                    m.getCreatedDate(), view.url(), download.url(), view.expiresAt());
        }
    }

    @Schema(description = "Summary cards and sidebar storage")
    /** storageLimitBytes: the company plan's storage limit; null = unlimited or several companies / the platform. */
    public record StatsDto(long files, long storageBytes, long images, Long storageLimitBytes) {
    }

    public record FolderForm(
            @Schema(description = "Folder name, unique per owner (case-insensitive)", example = "Logos",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String name) {
    }

    public record FileUpdateForm(
            @Schema(description = "Display name", example = "Company logo", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String displayName,
            @Schema(description = "Folder id of the same owner; omitted = no folder (PUT is a full replacement)", example = "3")
            Long folderId) {
    }
}
