package com.terp.erp.iam.integration;

import com.terp.erp.iam.model.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Whether one chat channel (Slack, Telegram, SMS) fires for one routed event of a company. No row for a channel of a
 * company = every event on (the behaviour before per-event switches); once saved, one row per catalog event.
 */
@Getter
@Setter
@Entity
@Table(name = "company_channel_event")
public class CompanyChannelEvent extends BaseAuditableEntity {
    public enum EChatChannel {
        SLACK("slack"), TELEGRAM("telegram"), SMS("sms");

        public final String path;

        EChatChannel(String path) {
            this.path = path;
        }
    }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private EChatChannel channel;

    /** EChannelEvent.eventType (the broker routing key). */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
