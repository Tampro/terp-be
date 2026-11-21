-- liquibase formatted sql

-- changeset ERP-IAM:20261119220000-1
-- Platform settings (docs/analysis/06 §3.3.2): one row per section, value = JSON of that section.
-- Secrets inside a value (SMTP password, reCAPTCHA secret) are AES-GCM encrypted with ERP_SETTINGS_ENC_KEY.
CREATE TABLE platform_setting
(
    section      VARCHAR(40) NOT NULL PRIMARY KEY,
    value_json   TEXT        NOT NULL,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
