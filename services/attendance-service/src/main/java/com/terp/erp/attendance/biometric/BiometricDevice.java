package com.terp.erp.attendance.biometric;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.attendance.common.BaseAuditableEntity;
import com.terp.erp.attendance.common.EStatus;

import java.time.Instant;

/** A biometric device of a company. It sends punches with its own key; only the key's SHA-256 is stored. */
@Getter
@Setter
@Entity
@Table(name = "biometric_device")
public class BiometricDevice extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "serial_number", nullable = false, length = 100)
    private String serialNumber;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "api_key_hash", nullable = false, length = 64)
    private String apiKeyHash;

    @Column(name = "key_prefix", nullable = false, length = 8)
    private String keyPrefix;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
