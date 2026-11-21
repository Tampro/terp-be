package com.terp.erp.employee.holiday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

/** company_id null = system category (seeded: National, Religious, Company Specific, Regional), read-only. */
@Getter
@Setter
@Entity
@Table(name = "holiday_category")
public class HolidayCategory extends BaseAuditableEntity {
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Calendar legend colour, #RRGGBB. */
    @Column(name = "color", nullable = false, length = 7)
    private String color;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
