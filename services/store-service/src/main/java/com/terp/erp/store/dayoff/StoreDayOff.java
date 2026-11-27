package com.terp.erp.store.dayoff;

import com.terp.erp.store.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A whole local date (in the store's time zone) on which the store takes no order. */
@Getter
@Setter
@Entity
@Table(name = "store_day_off")
public class StoreDayOff extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "off_date", nullable = false)
    private LocalDate offDate;

    @Column(name = "reason", length = 255)
    private String reason;
}
