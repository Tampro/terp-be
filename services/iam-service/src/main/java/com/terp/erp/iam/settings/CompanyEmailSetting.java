package com.terp.erp.iam.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.settings.SettingsDtos.EMailEncryption;

import java.time.Instant;

/** The company's own SMTP (docs/analysis/06 §10.5.4); the password is stored encrypted only. */
@Getter
@Setter
@Entity
@Table(name = "company_email_setting")
public class CompanyEmailSetting {
    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "host", nullable = false, length = 255)
    private String host;

    @Column(name = "port", nullable = false)
    private int port;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "password_enc", length = 1000)
    private String passwordEnc;

    @Enumerated(EnumType.STRING)
    @Column(name = "encryption", nullable = false, length = 10)
    private EMailEncryption encryption;

    @Column(name = "from_address", nullable = false, length = 255)
    private String fromAddress;

    @Column(name = "from_name", length = 100)
    private String fromName;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_date")
    private Instant updatedDate;
}
