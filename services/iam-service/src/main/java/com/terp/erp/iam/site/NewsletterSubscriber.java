package com.terp.erp.iam.site;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

/** An email subscribed to the newsletter from the landing page (docs/analysis/06 §3.2.10). */
@Getter
@Setter
@Entity
@Table(name = "newsletter_subscriber")
public class NewsletterSubscriber extends BaseAuditableEntity {
    @Column(name = "email", nullable = false)
    private String email;

    /** Put in the unsubscribe link of every newsletter email; it only allows removing this address. */
    @Column(name = "unsubscribe_token", nullable = false, length = 64)
    private String unsubscribeToken;
}
