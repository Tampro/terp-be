package com.terp.erp.meeting.zoom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.meeting.common.BaseAuditableEntity;

import java.time.Instant;

/** Zoom Server-to-Server OAuth credentials of a company; the secret is stored encrypted only. */
@Getter
@Setter
@Entity
@Table(name = "zoom_setting")
public class ZoomSetting extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "client_secret_cipher", nullable = false, length = 500)
    private String clientSecretCipher;

    /** The last Test Connection succeeded with these credentials. */
    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private Instant verifiedAt;
}
