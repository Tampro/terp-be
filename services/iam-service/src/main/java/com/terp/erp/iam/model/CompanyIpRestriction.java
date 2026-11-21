package com.terp.erp.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** The IP restriction switch of a company (docs/analysis/06 §10.5.6). */
@Getter
@Setter
@Entity
@Table(name = "company_ip_restriction")
public class CompanyIpRestriction {
    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_date")
    private Instant updatedDate;
}
