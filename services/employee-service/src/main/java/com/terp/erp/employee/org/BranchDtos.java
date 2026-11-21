package com.terp.erp.employee.org;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class BranchDtos {
    private BranchDtos() {
    }

    /** List row and detail; the counts are filled only on the detail (View). */
    public record BranchDto(String id, String companyId, String name, String address, String city, String state,
                            String country, String postalCode, String phone, String email, EOrgStatus status,
                            Instant createdDate, Instant updatedDate, Long departmentCount, Long employeeCount) {
        static BranchDto of(Branch b, Long departments, Long employees) {
            return new BranchDto(b.getId().toString(), b.getCompanyId().toString(), b.getName(), b.getAddress(),
                    b.getCity(), b.getState(), b.getCountry(), b.getPostalCode(), b.getPhone(), b.getEmail(),
                    b.getStatus(), b.getCreatedDate(), b.getUpdatedDate(), departments, employees);
        }

        static BranchDto of(Branch b) {
            return of(b, null, null);
        }
    }

    /** For dropdowns (Add Employee, Add Department): active branches only. */
    public record BranchOption(String id, String name) {
    }

    public record BranchForm(
            @Schema(example = "Head office", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(example = "1 Example Street") @Size(max = 255) String address,
            @Schema(example = "Ha Noi") @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Schema(example = "Viet Nam") @Size(max = 100) String country,
            @Size(max = 20) String postalCode,
            @Schema(example = "0240000000") @Size(max = 30) String phone,
            @Schema(example = "hq@example.com") @Email @Size(max = 255) String email,
            @Schema(description = "Omitted on create = ACTIVE; required meaning on update (full replacement)")
            EOrgStatus status) {
    }

    public record BranchStatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EOrgStatus status) {
    }
}
