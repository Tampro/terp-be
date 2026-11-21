package com.terp.erp.iam.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

/** A company's switch for one template. No row = on. */
@Getter
@Setter
@Entity
@Table(name = "company_email_notification")
public class CompanyEmailNotification extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "template_code", nullable = false, length = 50)
    private String templateCode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
