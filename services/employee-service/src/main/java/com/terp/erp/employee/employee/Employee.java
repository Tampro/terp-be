package com.terp.erp.employee.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An employee of a company (docs/analysis/06 §6.1). The login account is an IAM user ({@code userId}); its
 * enabled flag is mirrored in {@code loginEnabled} for the list. Bank account number and tax payer id are stored
 * encrypted (FieldCipher).
 */
@Getter
@Setter
@Entity
@Table(name = "employee")
public class Employee extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "login_enabled", nullable = false)
    private boolean loginEnabled;

    /** "EMP000111", issued by the system per company. */
    @Column(name = "employee_no", nullable = false, length = 20)
    private String employeeNo;

    /** Code typed by HR to match the biometric device. */
    @Column(name = "employee_code", length = 50)
    private String employeeCode;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private EGender gender;

    @Column(name = "profile_media_id")
    private Long profileMediaId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "designation_id")
    private Long designationId;

    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;

    @Column(name = "date_of_leaving")
    private LocalDate dateOfLeaving;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EEmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EEmployeeStatus status;

    /** Not checked yet: shifts and attendance policies belong to the attendance service (§6.4). */
    @Column(name = "shift_id")
    private Long shiftId;

    @Column(name = "attendance_policy_id")
    private Long attendancePolicyId;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "emergency_name", length = 255)
    private String emergencyName;

    @Column(name = "emergency_relationship", length = 50)
    private String emergencyRelationship;

    @Column(name = "emergency_phone", length = 30)
    private String emergencyPhone;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "account_holder_name", length = 255)
    private String accountHolderName;

    @Column(name = "account_number_enc", length = 255)
    private String accountNumberEnc;

    @Column(name = "bank_identifier_code", length = 20)
    private String bankIdentifierCode;

    @Column(name = "bank_branch", length = 150)
    private String bankBranch;

    @Column(name = "tax_payer_id_enc", length = 255)
    private String taxPayerIdEnc;

    @Column(name = "base_salary", precision = 15, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "reports_to_id")
    private Long reportsToId;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
