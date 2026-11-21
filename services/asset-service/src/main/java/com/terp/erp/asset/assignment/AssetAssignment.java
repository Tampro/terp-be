package com.terp.erp.asset.assignment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.asset.common.BaseAuditableEntity;
import com.terp.erp.asset.item.Asset.ECondition;

import java.time.LocalDate;

/** One checkout of an asset to an employee, open until it is returned (docs/analysis/06 §8.2.2). */
@Getter
@Setter
@Entity
@Table(name = "asset_assignment")
public class AssetAssignment extends BaseAuditableEntity {
    public enum EStatus { ASSIGNED, RETURNED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_name", length = 200)
    private String employeeName;

    @Column(name = "employee_no", length = 50)
    private String employeeNo;

    @Column(name = "employee_email", length = 255)
    private String employeeEmail;

    @Column(name = "checkout_date", nullable = false)
    private LocalDate checkoutDate;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkout_condition", nullable = false, length = 10)
    private ECondition checkoutCondition;

    @Column(name = "checkout_notes", length = 1000)
    private String checkoutNotes;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_condition", length = 10)
    private ECondition returnCondition;

    @Column(name = "return_notes", length = 1000)
    private String returnNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ASSIGNED;
}
