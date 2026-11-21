package com.terp.erp.employee.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class EmployeeDtos {
    private EmployeeDtos() {
    }

    // ------------------------------------------------------------------ responses

    public record Ref(String id, String name) {
    }

    public record EmployeeListItem(String id, String employeeNo, String employeeCode, String fullName, String email,
                                   String profileImageUrl, Ref branch, Ref department, Ref designation,
                                   EEmployeeStatus status, EEmploymentType employmentType, LocalDate dateOfJoining,
                                   @Schema(description = "false = no login account or the account is locked")
                                   boolean loginEnabled,
                                   @Schema(description = "Shift and attendance policy (attendance-service ids)")
                                   String shiftId, String attendancePolicyId) {
    }

    public record StatusCounts(long all, long active, long inactive, long probation, long terminated) {
    }

    /** Present only for callers with EMPLOYEE_BANKING_VIEW; otherwise the field is null. */
    public record BankingDto(String bankName, String accountHolderName, String accountNumber, String bankIdentifierCode,
                             String bankBranch, String taxPayerId, BigDecimal baseSalary) {
    }

    public record DocumentDto(String id, Ref documentType, String fileId, String fileName, String mimeType,
                              long sizeBytes, String viewUrl, String downloadUrl, LocalDate expiryDate,
                              @Schema(description = "expiryDate is before today") boolean expired,
                              boolean verified, Instant verifiedAt) {
    }

    public record EmployeeDetail(String id, String companyId, String employeeNo, String employeeCode, String fullName,
                                 String email, String phone, LocalDate dateOfBirth, EGender gender,
                                 String profileImageId, String profileImageUrl,
                                 Ref branch, Ref department, Ref designation, LocalDate dateOfJoining,
                                 LocalDate dateOfLeaving,
                                 EEmploymentType employmentType, EEmployeeStatus status, String shiftId,
                                 String attendancePolicyId, Ref reportsTo,
                                 String addressLine1, String addressLine2, String city, String state, String country,
                                 String postalCode, String emergencyName, String emergencyRelationship,
                                 String emergencyPhone, BankingDto banking,
                                 @Schema(description = "IAM user id of the login account") String userId,
                                 boolean loginEnabled, List<DocumentDto> documents,
                                 @Schema(description = "Required document types without an uploaded document")
                                 List<Ref> missingRequiredDocuments,
                                 Instant createdDate, Instant updatedDate) {
    }

    // ------------------------------------------------------------------ forms

    public record BankingForm(
            @Schema(example = "Vietcombank") @Size(max = 150) String bankName,
            @Schema(example = "NGUYEN VAN A") @Size(max = 255) String accountHolderName,
            @Schema(description = "Stored encrypted", example = "0123456789") @Size(max = 50) String accountNumber,
            @Schema(description = "BIC / SWIFT", example = "BFTVVNVX") @Size(max = 20) String bankIdentifierCode,
            @Schema(example = "Ha Noi") @Size(max = 150) String bankBranch,
            @Schema(description = "Stored encrypted", example = "8000000000") @Size(max = 50) String taxPayerId,
            @Schema(example = "15000000") @DecimalMin("0") BigDecimal baseSalary) {
    }

    /** Everything an administrator edits after creation (email and password are not here: they are the login). */
    public record ProfileForm(
            @Schema(example = "Nguyen Van A", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String fullName,
            @Schema(description = "Code used by the biometric device, unique in the company", example = "EMP001")
            @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9._-]*") String employeeCode,
            @Schema(example = "0900000000") @Size(max = 30) String phone,
            @Past LocalDate dateOfBirth,
            EGender gender,
            @Schema(description = "Media file id of an image of the company (max 2 MB)") Long profileImageId,
            Long branchId,
            @Schema(description = "Needs branchId") Long departmentId,
            @Schema(description = "Needs departmentId") Long designationId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate dateOfJoining,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EEmploymentType employmentType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EEmployeeStatus status,
            @Schema(description = "Not checked until the attendance module exists") Long shiftId,
            @Schema(description = "Not checked until the attendance module exists") Long attendancePolicyId,
            @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Size(max = 100) String country,
            @Size(max = 20) String postalCode,
            @Size(max = 255) String emergencyName,
            @Schema(example = "Spouse") @Size(max = 50) String emergencyRelationship,
            @Size(max = 30) String emergencyPhone,
            @Schema(description = "Ignored without EMPLOYEE_BANKING_VIEW") @Valid BankingForm banking,
            @Schema(description = "Employee this one reports to (Organization Chart); same company, no cycle")
            Long reportsToId,
            @Schema(description = "Last working day ({leaving_date} of the Experience Certificate); not before "
                    + "dateOfJoining")
            LocalDate dateOfLeaving) {
    }

    public record DocumentForm(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long documentTypeId,
            @Schema(description = "Media file id of the company (max 5 MB)", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long fileId,
            LocalDate expiryDate) {
    }

    public record EmployeeCreateForm(
            @Schema(description = "Also the login name of the account", example = "a.nguyen@example.com",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 100) String email,
            @Schema(description = "First password, changed by the employee at the first login. Omitted = generated "
                    + "by IAM. Sent by email either way.")
            @Size(min = 8, max = 128) String password,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Valid ProfileForm profile,
            List<@Valid DocumentForm> documents) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EEmployeeStatus status) {
    }

    public record LoginForm(
            @Schema(description = "true = account can sign in", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Boolean enabled) {
    }

    public record PasswordForm(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 8, max = 128) String newPassword,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String confirmPassword) {
    }

    public record VerifyForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean verified) {
    }

    /** Node of the Organization Chart; children = direct reports. */
    public record ChartNode(String id, String fullName, String designation, String profileImageUrl,
                            @Schema(description = "green dot: ACTIVE or PROBATION; red: INACTIVE") boolean active,
                            List<ChartNode> children) {
    }

    /** Company node at the top; members = everyone in the chart. */
    public record OrgChart(String companyName, int members, List<ChartNode> children) {
    }

    /** List filters, shared by the list, the status tabs and the export. */
    /** ids = only these employees (other services resolving names in one call); null = no such filter. */
    public record Filter(String keyword, Long branchId, Long departmentId, Long designationId, EEmployeeStatus status,
                         @Schema(description = "true = only employees missing a Required document type")
                         Boolean missingRequiredDocuments, java.util.Set<Long> ids) {
        public Filter(String keyword, Long branchId, Long departmentId, Long designationId, EEmployeeStatus status) {
            this(keyword, branchId, departmentId, designationId, status, null, null);
        }

        public Filter(String keyword, Long branchId, Long departmentId, Long designationId, EEmployeeStatus status,
                      Boolean missingRequiredDocuments) {
            this(keyword, branchId, departmentId, designationId, status, missingRequiredDocuments, null);
        }
    }
}
