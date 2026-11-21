package com.terp.erp.attendance.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.attendance.common.BaseAuditableEntity;

import java.time.Instant;

/** One press of Sync / Re-sync (Leave Balance Sync History). createdDate is the "Recorded At". */
@Getter
@Setter
@Entity
@Table(name = "leave_balance_sync")
public class LeaveBalanceSync extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "sync_year", nullable = false)
    private int year;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "synced_by", length = 150)
    private String syncedBy;

    @Column(name = "employees", nullable = false)
    private int employees;

    @Column(name = "balances", nullable = false)
    private int balances;
}
