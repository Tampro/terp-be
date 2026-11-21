package com.terp.erp.iam.impersonation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

import java.time.Instant;

/** One "Login as Company" session of a super admin, kept for audit. */
@Getter
@Setter
@Entity
@Table(name = "impersonation_session")
public class ImpersonationSession extends BaseAuditableEntity {
    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "super_admin_sub", nullable = false, length = 64)
    private String superAdminSub;

    @Column(name = "super_admin_username", length = 100)
    private String superAdminUsername;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public boolean activeAt(Instant now) {
        return endedAt == null && expiresAt.isAfter(now);
    }
}
