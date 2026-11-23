package com.terp.erp.iam.template;

import com.terp.erp.iam.model.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A company's override of one notification text in one language. No row = notification-service's built-in text. */
@Getter
@Setter
@Entity
@Table(name = "company_notification_template")
public class CompanyNotificationTemplate extends BaseAuditableEntity {
    public enum ETemplateChannel { EMAIL, SLACK, TELEGRAM, SMS }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private ETemplateChannel channel;

    /** EMAIL: ENotificationEvent code; chat channels: EChannelEvent routing key. */
    @Column(name = "template_key", nullable = false, length = 100)
    private String templateKey;

    @Column(name = "lang", nullable = false, length = 5)
    private String lang;

    /** EMAIL only. */
    @Column(name = "subject", length = 255)
    private String subject;

    /** EMAIL: sanitised HTML; chat: plain text. */
    @Column(name = "body", nullable = false)
    private String body;
}
