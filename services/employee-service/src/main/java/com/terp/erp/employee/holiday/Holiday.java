package com.terp.erp.employee.holiday;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** One occurrence (no yearly repetition); an empty branch set means every branch of the company. */
@Getter
@Setter
@Entity
@Table(name = "holiday")
public class Holiday extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "description", length = 500)
    private String description;

    /** Is Paid (FR-ERP-HOL-001): a paid public holiday; existing rows default to paid. */
    @Column(name = "is_paid", nullable = false)
    private boolean paid = true;

    @ElementCollection
    @CollectionTable(name = "holiday_branch", joinColumns = @JoinColumn(name = "holiday_id"))
    @Column(name = "branch_id", nullable = false)
    private Set<Long> branchIds = new HashSet<>();

    /** Calendar sync (ERP manual: holidays): notification-service adds the holiday to the company's Google Calendar. */
    @Column(name = "calendar_sync", nullable = false)
    private boolean calendarSync;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
