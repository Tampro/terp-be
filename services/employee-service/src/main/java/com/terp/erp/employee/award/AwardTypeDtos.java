package com.terp.erp.employee.award;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.terp.erp.employee.org.EOrgStatus;

import java.time.Instant;

public final class AwardTypeDtos {
    private AwardTypeDtos() {
    }

    public record AwardTypeDto(String id, String companyId, String name, String description, String icon,
                               EOrgStatus status, Instant createdDate, Instant updatedDate) {
        static AwardTypeDto of(AwardType t) {
            return new AwardTypeDto(t.getId().toString(), t.getCompanyId().toString(), t.getName(), t.getDescription(),
                    t.getIcon(), t.getStatus(), t.getCreatedDate(), t.getUpdatedDate());
        }
    }

    /** For dropdowns (giving an award): active types only. */
    public record AwardTypeOption(String id, String name, String icon) {
    }

    public record AwardTypeForm(
            @Schema(example = "Employee of the Month", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(example = "Outstanding performance during the month") @Size(max = 500) String description,
            @Schema(description = "Key of the FE icon set; omitted = default icon", example = "trophy")
            @Size(max = 50) @Pattern(regexp = "^[a-z0-9-]*$") String icon,
            @Schema(description = "Omitted on create = ACTIVE; omitted on update = unchanged") EOrgStatus status) {
    }

    public record AwardTypeStatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EOrgStatus status) {
    }
}
