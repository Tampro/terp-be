package com.terp.erp.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A one-time link: password reset (docs/analysis/06 §2.4, of a user) or email verification (§2.5, of a company).
 * Only the SHA-256 of the token is stored, so a database copy cannot be used to reset anyone's password.
 */
@Getter
@Setter
@Entity
@Table(name = "account_token")
public class AccountToken extends BaseAuditableEntity {
    public enum EPurpose { PASSWORD_RESET, EMAIL_VERIFY }

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private EPurpose purpose;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
