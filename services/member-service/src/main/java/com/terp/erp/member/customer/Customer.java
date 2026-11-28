package com.terp.erp.member.customer;

import com.terp.erp.member.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One membership of a customer (customer realm subject) in one shop's company, with the consents given. */
@Getter
@Setter
@Entity
@Table(name = "customer")
public class Customer extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "keycloak_sub", nullable = false, length = 64)
    private String keycloakSub;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ECustomerStatus status;

    @Column(name = "terms_version", nullable = false, length = 20)
    private String termsVersion;

    @Column(name = "terms_agreed_at", nullable = false)
    private Instant termsAgreedAt;

    @Column(name = "privacy_version", nullable = false, length = 20)
    private String privacyVersion;

    @Column(name = "privacy_agreed_at", nullable = false)
    private Instant privacyAgreedAt;

    @Column(name = "marketing_agreed", nullable = false)
    private boolean marketingAgreed;

    @Column(name = "marketing_agreed_at")
    private Instant marketingAgreedAt;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    /** 0 while ACTIVE, the row id once LEFT — frees uk_customer_live for a new membership. */
    @Column(name = "live_key", nullable = false)
    private long liveKey;
}
