package com.terp.erp.accounting.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Which account of the company plays a system role; (company, key) is unique. */
@Getter
@Setter
@Entity
@Table(name = "account_mapping")
public class SystemAccountMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_key", nullable = false, length = 30, updatable = false)
    private ESystemAccount systemKey;

    @Column(name = "account_id", nullable = false)
    private Long accountId;
}
