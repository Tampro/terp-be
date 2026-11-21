package com.terp.erp.iam.helpdesk;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class HelpdeskDtos {
    private HelpdeskDtos() {
    }

    public record CategoryDto(String id, String name, String description, String color, boolean active,
                              Instant createdDate, Instant updatedDate) {
        static CategoryDto of(HelpdeskCategory c) {
            return new CategoryDto(c.getId().toString(), c.getName(), c.getDescription(), c.getColor(), c.isActive(),
                    c.getCreatedDate(), c.getUpdatedDate());
        }
    }

    /** Create and full-replacement edit of a category. */
    public record CategoryForm(
            @Schema(description = "Name", example = "Billing", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String name,
            @Schema(description = "Description", example = "Plans, invoices and payments")
            @Size(max = 500) String description,
            @Schema(description = "Badge colour #RRGGBB", example = "#3B82F6", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String color,
            @Schema(description = "Only active categories can be chosen for a ticket", example = "true",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Boolean active) {
    }

    public record TicketRow(String id, String ticketNumber, String title, String companyId, String companyName,
                            String categoryId, String categoryName, String categoryColor, EHelpdeskStatus status,
                            EHelpdeskPriority priority, Instant createdDate, Instant updatedDate, Instant resolvedAt) {
    }

    public record ReplyDto(String id, String message, boolean internal, boolean fromPlatform, String authorName,
                           List<String> attachmentFileIds, Instant createdDate) {
        static ReplyDto of(HelpdeskReply r) {
            return new ReplyDto(r.getId().toString(), r.getMessage(), r.isInternal(), r.isFromPlatform(),
                    r.getAuthorName(), r.getAttachmentFileIds().stream().map(String::valueOf).toList(),
                    r.getCreatedDate());
        }
    }

    /** One ticket with its conversation; a company never receives internal replies. */
    public record TicketDetail(TicketRow ticket, String description, List<ReplyDto> replies) {
    }

    public record TicketCreateForm(
            @Schema(description = "Super admin only: the company the ticket is for (a company user always gets their "
                    + "own)", example = "12")
            Long companyId,
            @Schema(description = "Title", example = "Cannot open the payroll report",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String title,
            @Schema(description = "Description (rich text HTML)", example = "<p>The page stays blank since Monday.</p>",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 20000) String description,
            @Schema(description = "Active category id", example = "3")
            Long categoryId,
            @Schema(description = "Super admin only; a company's ticket starts at MEDIUM", example = "HIGH")
            EHelpdeskPriority priority) {
    }

    /** Super admin: full replacement of the ticket's fields. */
    public record TicketUpdateForm(
            @Schema(description = "Title", example = "Cannot open the payroll report",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String title,
            @Schema(description = "Description (rich text HTML)", example = "<p>The page stays blank since Monday.</p>",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 20000) String description,
            @Schema(description = "Category id (active or the one already set); null = none", example = "3")
            Long categoryId,
            @Schema(description = "Status", example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EHelpdeskStatus status,
            @Schema(description = "Priority", example = "HIGH", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EHelpdeskPriority priority) {
    }

    public record ReplyForm(
            @Schema(description = "Message (rich text HTML)", example = "<p>We are looking into it.</p>",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 20000) String message,
            @Schema(description = "Media file ids of the ticket's company (image, PDF, text, Office), up to 5",
                    example = "[\"41\"]")
            @Size(max = 5) List<@NotNull Long> attachmentFileIds,
            @Schema(description = "Super admin only: a note the company does not see", example = "false")
            Boolean internal) {
    }

    public record MonthStat(int month, long created, long resolved) {
    }

    /** Super admin dashboard: tickets created vs resolved per month of the year, and the current status split. */
    public record HelpdeskStats(int year, List<MonthStat> months, Map<EHelpdeskStatus, Long> byStatus, long total) {
    }
}
