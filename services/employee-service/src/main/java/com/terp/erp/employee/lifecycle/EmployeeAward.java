package com.terp.erp.employee.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.time.LocalDate;

/** An award given to an employee (docs/analysis/06 §7.2.1). */
@Getter
@Setter
@Entity
@Table(name = "employee_award")
public class EmployeeAward extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "award_type_id", nullable = false)
    private Long awardTypeId;

    @Column(name = "award_date", nullable = false)
    private LocalDate awardDate;

    @Column(name = "gift", length = 255)
    private String gift;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "certificate_media_id")
    private Long certificateMediaId;

    @Column(name = "photo_media_id")
    private Long photoMediaId;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
