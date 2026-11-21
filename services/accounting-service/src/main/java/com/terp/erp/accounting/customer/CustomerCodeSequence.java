package com.terp.erp.accounting.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Last customer number issued in a company; numbers only grow, so a deleted customer's code is never reused. */
@Getter
@Setter
@Entity
@Table(name = "customer_code_sequence")
public class CustomerCodeSequence {
    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;
}
