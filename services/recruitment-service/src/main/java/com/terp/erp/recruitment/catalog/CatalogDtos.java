package com.terp.erp.recruitment.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.terp.erp.recruitment.common.EStatus;

import java.time.Instant;

public final class CatalogDtos {
    private CatalogDtos() {
    }

    public record CatalogDto(String id, ECatalogKind kind, String name, String description, EStatus status,
                             Instant createdDate, Instant updatedDate) {
        static CatalogDto of(CatalogItem c) {
            return new CatalogDto(c.getId().toString(), c.getKind(), c.getName(), c.getDescription(), c.getStatus(),
                    c.getCreatedDate(), c.getUpdatedDate());
        }
    }

    /** Dropdowns: active items. */
    public record Option(String id, String name) {
    }

    public record CatalogForm(
            @Schema(example = "Engineering", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(example = "Software and IT roles") @Size(max = 1000) String description,
            @Schema(description = "Omitted on create = ACTIVE; omitted on update = unchanged") EStatus status) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }

    public record StatusCounts(long all, long active, long inactive) {
    }
}
