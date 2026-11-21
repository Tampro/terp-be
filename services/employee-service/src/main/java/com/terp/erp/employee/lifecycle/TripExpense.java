package com.terp.erp.employee.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One expense line of a trip (Trip Expenses page; PO, 2026-11-20). */
@Getter
@Setter
@Entity
@Table(name = "trip_expense")
public class TripExpense extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "expense_type_id", nullable = false)
    private Long expenseTypeId;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "receipt_media_id")
    private Long receiptMediaId;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
