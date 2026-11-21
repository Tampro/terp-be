package com.terp.erp.employee.org;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class DepartmentDtos {
    private DepartmentDtos() {
    }

    public record BranchRef(String id, String name) {
    }

    /** List row and detail; the counts are filled only on the detail (View). */
    public record DepartmentDto(String id, String companyId, String name, String description, BranchRef branch,
                                EOrgStatus status, Instant createdDate, Instant updatedDate, Long designationCount,
                                Long employeeCount) {
        static DepartmentDto of(Department d, String branchName, Long designations, Long employees) {
            return new DepartmentDto(d.getId().toString(), d.getCompanyId().toString(), d.getName(), d.getDescription(),
                    new BranchRef(d.getBranchId().toString(), branchName), d.getStatus(), d.getCreatedDate(),
                    d.getUpdatedDate(), designations, employees);
        }
    }

    /** For dropdowns: active departments, of one branch when asked. */
    public record DepartmentOption(String id, String name, String branchId) {
    }

    public record StatusCounts(long all, long active, long inactive) {
    }

    public record DepartmentForm(
            @Schema(example = "Human Resources", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(description = "Branch id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long branchId,
            @Schema(example = "People operations") @Size(max = 500) String description,
            @Schema(description = "Omitted on create = ACTIVE; omitted on update = unchanged") EOrgStatus status) {
    }

    public record DepartmentStatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EOrgStatus status) {
    }
}
