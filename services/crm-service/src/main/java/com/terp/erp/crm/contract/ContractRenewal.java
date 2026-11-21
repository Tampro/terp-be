package com.terp.erp.crm.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A renewal period of a contract (ERP manual 7.5 Renewals: start, end, value, status, notes). */
@Getter
@Setter
@Entity
@Table(name = "contract_renewal")
public class ContractRenewal extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "value", nullable = false, precision = 19, scale = 4)
    private BigDecimal value;

    // TODO(assumption): TERP Core only says status string default 'pending'; the contract statuses are reused.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Contract.EStatus status = Contract.EStatus.PENDING;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
