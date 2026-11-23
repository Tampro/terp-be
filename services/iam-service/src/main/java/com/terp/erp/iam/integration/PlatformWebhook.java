package com.terp.erp.iam.integration;

import com.terp.erp.iam.model.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A super admin webhook for one platform event (ERP manual 3.7 "Webhooks: method, event, URL"). No company. */
@Getter
@Setter
@Entity
@Table(name = "platform_webhook")
public class PlatformWebhook extends BaseAuditableEntity {
    @Enumerated(EnumType.STRING)
    @Column(name = "event_code", nullable = false, length = 60)
    private EPlatformWebhookEvent eventCode;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    /** POST only, like company webhooks (see CompanyWebhook.EMethod). */
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 10)
    private CompanyWebhook.EMethod method = CompanyWebhook.EMethod.POST;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** HMAC-SHA256 signing secret, AES-GCM encrypted (SecretCipher); null = unsigned. */
    @Column(name = "secret_enc", length = 1000)
    private String secretEnc;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
