package com.terp.erp.employee.award;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;
import com.terp.erp.employee.org.EOrgStatus;

/** A kind of employee recognition (docs/analysis/06 §6.3.6); inactive ones cannot be given in new awards. */
@Getter
@Setter
@Entity
@Table(name = "award_type")
public class AwardType extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** Key of the FE icon set (e.g. "trophy"), not a URL. */
    @Column(name = "icon", length = 50)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EOrgStatus status = EOrgStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
