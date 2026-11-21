package com.terp.erp.employee.org;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class DesignationDtos {
    private DesignationDtos() {
    }

    public record Ref(String id, String name) {
    }

    /** List row and detail: the department and the branch it belongs to; employeeCount only on the detail (View). */
    public record DesignationDto(String id, String companyId, String name, String description, Ref department,
                                 Ref branch, EOrgStatus status, Instant createdDate, Instant updatedDate,
                                 Long employeeCount) {
        static DesignationDto of(Designation d, Department dep, String branchName, Long employees) {
            Ref department = new Ref(d.getDepartmentId().toString(), dep == null ? null : dep.getName());
            Ref branch = dep == null ? null : new Ref(dep.getBranchId().toString(), branchName);
            return new DesignationDto(d.getId().toString(), d.getCompanyId().toString(), d.getName(),
                    d.getDescription(), department, branch, d.getStatus(), d.getCreatedDate(), d.getUpdatedDate(),
                    employees);
        }
    }

    /** For dropdowns: active designations, of one department when asked. */
    public record DesignationOption(String id, String name, String departmentId) {
    }

    public record DesignationForm(
            @Schema(example = "IT Manager", description = "Unique within the department",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(description = "Department id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long departmentId,
            @Schema(example = "Leads the IT team") @Size(max = 500) String description,
            @Schema(description = "Omitted on create = ACTIVE; omitted on update = unchanged") EOrgStatus status) {
    }

    public record DesignationStatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EOrgStatus status) {
    }
}
