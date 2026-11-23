package com.terp.erp.employee.holiday;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class HolidayDtos {
    private HolidayDtos() {
    }

    public record Ref(String id, String name) {
    }

    /** system = shared by every company, read-only. */
    public record CategoryDto(String id, String name, String color, boolean system) {
        static CategoryDto of(HolidayCategory c) {
            return new CategoryDto(c.getId().toString(), c.getName(), c.getColor(), c.getCompanyId() == null);
        }
    }

    /** allBranches = no branch chosen: the holiday applies to every branch, also ones created later. */
    public record HolidayDto(String id, String companyId, String name, CategoryDto category, LocalDate startDate,
                             LocalDate endDate, long days, String description, boolean isPaid,
                             boolean allBranches,
                             List<Ref> branches, Instant createdDate, Instant updatedDate, boolean calendarSync) {
        public HolidayDto(String id, String companyId, String name, CategoryDto category, LocalDate startDate,
                          LocalDate endDate, long days, String description, boolean isPaid, boolean allBranches,
                          List<Ref> branches, Instant createdDate, Instant updatedDate) {
            this(id, companyId, name, category, startDate, endDate, days, description, isPaid, allBranches, branches,
                    createdDate, updatedDate, false);
        }
    }

    public record HolidayForm(
            @Schema(example = "Christmas Day", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(description = "Holiday category id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long categoryId,
            @Schema(example = "2026-12-25", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
            @Schema(example = "2026-12-25", description = "Same as startDate for a one-day holiday",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate endDate,
            @Schema(example = "Christmas") @Size(max = 500) String description,
            @Schema(description = "Applicable branch ids; empty or omitted = all branches")
            @Size(max = 200) List<@NotNull Long> branchIds,
            @Schema(description = "Is Paid; omitted = true", example = "true") Boolean isPaid,
            @Schema(description = "Add the holiday to the company's Google Calendar (integration must be on); "
                    + "omitted = false", example = "false") Boolean calendarSync) {
        public HolidayForm(String name, Long categoryId, LocalDate startDate, LocalDate endDate, String description,
                           List<Long> branchIds, Boolean isPaid) {
            this(name, categoryId, startDate, endDate, description, branchIds, isPaid, null);
        }
    }

    public record CategoryForm(
            @Schema(example = "Local festival", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String name,
            @Schema(example = "#FB8C00", description = "Legend colour #RRGGBB", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color) {
    }

    /** Filters shared by the list, the calendar and the exports. */
    public record Filter(String keyword, Long categoryId, Integer year, LocalDate dateFrom, LocalDate dateTo,
                         Long branchId) {
    }
}
