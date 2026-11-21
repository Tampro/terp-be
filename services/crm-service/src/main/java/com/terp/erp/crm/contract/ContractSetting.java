package com.terp.erp.crm.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/** Contract numbering of one company: prefix + 4 digits (CON0001). The counter survives prefix changes. */
@Getter
@Setter
@Entity
@Table(name = "contract_setting")
@EntityListeners(AuditingEntityListener.class)
public class ContractSetting {
    public static final String DEFAULT_PREFIX = "CON";

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "prefix", nullable = false, length = 10)
    private String prefix = DEFAULT_PREFIX;

    @Column(name = "next_number", nullable = false)
    private int nextNumber = 1;

    @LastModifiedBy
    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @LastModifiedDate
    @Column(name = "updated_date")
    private Instant updatedDate;

    /** Number for the current counter, at least 4 digits. */
    public String format(int n) {
        return prefix + String.format("%04d", n);
    }
}
