package com.terp.erp.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.constant.ECompanyStatus;

/** Tenant. Every company-owned row in every service carries its id as {@code company_id}. */
@Getter
@Setter
@Entity
@Table(name = "company")
public class Company extends BaseAuditableEntity {
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    /** Contact email of the company (not a login). Null for companies created before it existed. */
    @Column(name = "email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ECompanyStatus status;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    /** When the company was deleted: starts the retention period (PO 2026-11-24: 5 years, then anonymised). */
    @Column(name = "deleted_at")
    private java.time.Instant deletedAt;

    /** Set once {@link com.terp.erp.iam.company.CompanyAnonymizer} has run; the row then holds no personal data. */
    @Column(name = "anonymized_at")
    private java.time.Instant anonymizedAt;

    /** Set once the accounting retention (10 years, Law on Accounting) has been signalled to the finance services. */
    @Column(name = "finance_anonymized_at")
    private java.time.Instant financeAnonymizedAt;

    /** Current plan; null = no subscription (internal / bootstrap company): no plan limits, never expires. */
    @Column(name = "plan_id")
    private Long planId;

    /** Null with a plan = does not expire (default plan). */
    @Column(name = "plan_expires_at")
    private java.time.Instant planExpiresAt;

    @Column(name = "on_trial", nullable = false)
    private boolean onTrial;

    /** This company's code for referring others: REF + 6-digit id (docs/analysis/06 §3.2.6). */
    @Column(name = "referral_code", length = 20)
    private String referralCode;

    /** The company whose code was given at creation; null = not referred. */
    @Column(name = "referred_by_company_id")
    private Long referredByCompanyId;

    /** Email verification (docs/analysis/06 §2.5); null = not verified. */
    @Column(name = "email_verified_at")
    private java.time.Instant emailVerifiedAt;

    /** Self-registered with Email Verification on: no login until the email is verified (AUTH_016). */
    @Column(name = "email_verify_required", nullable = false)
    private boolean emailVerifyRequired;
}
