package com.terp.erp.accounting.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One row per company once its default chart is seeded; locked while seeding so it runs only once. */
@Getter
@Setter
@Entity
@Table(name = "ledger_setup")
public class LedgerSetup {
    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "seeded", nullable = false)
    private boolean seeded;
}
