package com.terp.erp.iam.site;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

import java.time.Instant;

/** A message from the landing page's contact form (docs/analysis/06 §3.2.9). */
@Getter
@Setter
@Entity
@Table(name = "contact_inquiry")
public class ContactInquiry extends BaseAuditableEntity {
    public enum EStatus { NEW, CONTACTED, QUALIFIED, CONVERTED, CLOSED }

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EStatus status;

    @Column(name = "last_replied_at")
    private Instant lastRepliedAt;
}
