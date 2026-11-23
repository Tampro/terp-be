package com.terp.erp.notification.delivery;

import com.terp.erp.notification.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One send of one event on one channel to one target. target is masked (email: "*@domain"; Slack / Telegram /
 * webhook: the host; SMS: "*" + last 3 digits); last_error_code is a code (SMTP_ERROR, HTTP_500, URL_REFUSED…), never a body or an address.
 */
@Getter
@Setter
@Entity
@Table(name = "delivery")
public class Delivery extends BaseAuditableEntity {
    public enum EChannel { EMAIL, SLACK, TELEGRAM, WEBHOOK, SMS }

    /** PENDING = not sent yet or waiting for a retry (next_attempt_at); SKIPPED = nothing to send (switch off…). */
    public enum EStatus { PENDING, SENT, FAILED, SKIPPED }

    /** null = a platform event (EPlatformRoute): visible to super admins only. */
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private EChannel channel;

    @Column(name = "target", length = 255)
    private String target;

    /** EMAIL: "payload" (the event's recipientEmail) or "user:&lt;id&gt;"; WEBHOOK: null (see webhookId). */
    @Column(name = "recipient_ref", length = 40)
    private String recipientRef;

    @Column(name = "webhook_id")
    private Long webhookId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error_code", length = 40)
    private String lastErrorCode;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
