package com.terp.erp.employee.org;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class DocumentTypeDtos {
    private DocumentTypeDtos() {
    }

    /**
     * system = shared by every company: name and description read-only, Required set per company.
     * defaultSlot = pre-filled in the Add Employee documents step. required = for the caller's company.
     */
    public record DocumentTypeDto(String id, String name, String description, boolean required, boolean system,
                                  boolean defaultSlot, Instant createdDate, Instant updatedDate) {
        static DocumentTypeDto of(DocumentType t, boolean required) {
            return new DocumentTypeDto(t.getId().toString(), t.getName(), t.getDescription(), required,
                    t.getCompanyId() == null, t.isDefaultSlot(), t.getCreatedDate(), t.getUpdatedDate());
        }
    }

    public record DocumentTypeForm(
            @Schema(example = "Work Permit", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(example = "Work permit of a foreign employee") @Size(max = 500) String description,
            @Schema(description = "Required for all employees (tracked, never blocks adding an employee)")
            boolean required) {
    }

    public record RequiredForm(@Schema(description = "true = Required, false = Optional") boolean required) {
    }
}
