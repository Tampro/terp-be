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

/** A contract with an accounting customer (ERP manual 7.5). customer_id is an accounting-service id. */
@Getter
@Setter
@Entity
@Table(name = "contract")
public class Contract extends BaseAuditableEntity {
    /** The five summary cards are Total plus these four. */
    public enum EStatus { PENDING, ACTIVE, CLOSED, DECLINED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "contract_number", nullable = false, length = 20)
    private String number;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "contract_type_id", nullable = false)
    private Long contractTypeId;

    @Column(name = "value", precision = 19, scale = 4)
    private BigDecimal value;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.PENDING;

    /** The agreement text (rich text from the editor). */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Display name of the user who created the contract, taken from the token at create. Null on contracts created
     * before the column existed. TODO(assumption): the owner is the creator; a contract has no assignee field.
     */
    @Column(name = "owner_name", length = 150)
    private String ownerName;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
