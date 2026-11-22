package com.terp.erp.iam.integration;

import com.terp.erp.iam.model.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A company's webhook for one event (reference webhooks: action, url, method, is_active). */
@Getter
@Setter
@Entity
@Table(name = "company_webhook")
public class CompanyWebhook extends BaseAuditableEntity {
    /** The reference also allows GET; an event payload belongs in a body, so POST only. */
    public enum EMethod { POST }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_code", nullable = false, length = 60)
    private EWebhookEvent eventCode;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 10)
    private EMethod method = EMethod.POST;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** HMAC-SHA256 signing secret, AES-GCM encrypted (SecretCipher); null = unsigned. */
    @Column(name = "secret_enc", length = 1000)
    private String secretEnc;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
