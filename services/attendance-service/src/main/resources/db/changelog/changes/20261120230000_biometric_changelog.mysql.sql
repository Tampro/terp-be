-- liquibase formatted sql

-- changeset ERP-ATTENDANCE:20261120230000-1
-- Biometric devices of a company (docs/analysis/06 §6.4.3; PO, 2026-11-19: device push with per-device keys). Only the
-- SHA-256 of the key is stored; key_prefix (first 8 characters) lets an admin tell keys apart.
CREATE TABLE biometric_device
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    name          VARCHAR(150) NOT NULL,
    serial_number VARCHAR(100) NOT NULL,
    location      VARCHAR(200) NULL,
    api_key_hash  CHAR(64)     NOT NULL,
    key_prefix    VARCHAR(8)   NOT NULL,
    status        VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    last_seen_at  DATETIME(6)  NULL,
    deleted       BIT(1)       NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    updated_by    VARCHAR(64)  NULL,
    created_date  DATETIME(6)  NULL,
    updated_date  DATETIME(6)  NULL,
    UNIQUE KEY uk_biometric_device_key (api_key_hash),
    INDEX idx_biometric_device_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ATTENDANCE:20261120230000-2
-- Punches as the device sent them. employee_code is the device's user id (= Employee Code, else Employee No): it is
-- matched to an employee only when an admin views or syncs, because a device call carries no user token. punched_at is
-- the company's wall-clock time. external_id makes a re-sent punch a no-op.
CREATE TABLE biometric_punch
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT       NOT NULL,
    device_id            BIGINT       NOT NULL,
    employee_code        VARCHAR(50)  NOT NULL,
    punched_at           DATETIME     NOT NULL,
    external_id          VARCHAR(100) NOT NULL,
    received_at          DATETIME(6)  NOT NULL,
    synced_at            DATETIME(6)  NULL,
    attendance_record_id BIGINT       NULL,
    UNIQUE KEY uk_biometric_punch_external (device_id, external_id),
    INDEX idx_biometric_punch_company_time (company_id, punched_at),
    CONSTRAINT fk_biometric_punch_device FOREIGN KEY (device_id) REFERENCES biometric_device (id),
    CONSTRAINT fk_biometric_punch_record FOREIGN KEY (attendance_record_id) REFERENCES attendance_record (id)
        ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
