package com.terp.erp.employee.announcement;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AnnouncementDtos {
    private AnnouncementDtos() {
    }

    public record Ref(String id, String name) {
    }

    /** system = shared by every company, read-only. */
    public record CategoryDto(String id, String name, String color, boolean system) {
        static CategoryDto of(AnnouncementCategory c) {
            return new CategoryDto(c.getId().toString(), c.getName(), c.getColor(), c.getCompanyId() == null);
        }
    }

    /**
     * viewUrl / downloadUrl are signed media links (10 minutes); null when the caller may not open the media library
     * (no MEDIA_VIEW) or the file is gone.
     */
    public record AttachmentDto(String fileId, String name, String mimeType, long sizeBytes, String viewUrl,
                                String downloadUrl) {
    }

    /**
     * List row, dashboard card and detail. content, attachment links and statistics are filled on the detail only;
     * contentPreview is plain text for the dashboard cards.
     */
    public record AnnouncementDto(String id, String companyId, String title, CategoryDto category,
                                  String shortDescription, String contentPreview, String content, LocalDate startDate,
                                  LocalDate endDate, EAnnouncementState state, boolean featured, boolean highPriority,
                                  boolean companyWide, List<Ref> branches, List<Ref> departments,
                                  List<AttachmentDto> attachments, Stats stats, Instant createdDate,
                                  Instant updatedDate) {
    }

    /** viewRate in percent, one decimal; 0 when the audience is empty. */
    public record Stats(long totalEmployees, long views, double viewRate) {
        static Stats of(long total, long views) {
            return new Stats(total, views, total == 0 ? 0 : Math.round(views * 1000.0 / total) / 10.0);
        }
    }

    public record TabCounts(long all, long highPriority, long featured, long upcoming) {
    }

    public record AnnouncementForm(
            @Schema(example = "Year-end party", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 200) String title,
            @Schema(description = "Announcement category id", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long categoryId,
            @Schema(example = "Join us on Friday evening") @Size(max = 500) String shortDescription,
            @Schema(description = "Rich text (HTML); scripts, styles and event handlers are removed",
                    example = "<p>Dinner at <b>7 pm</b>.</p>", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100_000) String content,
            @Schema(example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
            @Schema(example = "2026-12-31", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate endDate,
            @Schema(description = "Media library file ids of the company, in display order")
            @Size(max = 10) List<@NotNull Long> attachmentIds,
            boolean featured,
            boolean highPriority,
            @Schema(description = "true = every employee; target branches and departments are then ignored")
            boolean companyWide,
            @Schema(description = "Audience = employees of any of these branches or any of the departments")
            @Size(max = 200) List<@NotNull Long> branchIds,
            @Size(max = 500) List<@NotNull Long> departmentIds) {
    }

    public record CategoryForm(
            @Schema(example = "Training", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String name,
            @Schema(example = "#6D4C41", description = "Tag colour #RRGGBB", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color) {
    }

    /** Admin list filters. tab narrows to HIGH_PRIORITY, FEATURED or UPCOMING (dashboard tabs). */
    public record Filter(String keyword, Long categoryId, Long branchId, Long departmentId, LocalDate dateFrom,
                         LocalDate dateTo, ETab tab) {
    }

    public enum ETab {
        ALL, HIGH_PRIORITY, FEATURED, UPCOMING
    }
}
