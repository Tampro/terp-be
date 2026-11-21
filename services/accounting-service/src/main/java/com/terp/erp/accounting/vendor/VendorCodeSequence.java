package com.terp.erp.accounting.vendor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Last vendor number issued in a company; numbers only grow, so a deleted vendor's code is never reused. */
@Getter
@Setter
@Entity
@Table(name = "vendor_code_sequence")
public class VendorCodeSequence {
    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;
}
