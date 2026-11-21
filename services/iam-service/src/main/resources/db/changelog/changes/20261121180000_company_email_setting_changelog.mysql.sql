-- liquibase formatted sql

-- changeset ERP-IAM:20261121180000-1
-- Company email settings (docs/analysis/06 10.5.4: the company side has its own SMTP; PO 2026-11-21). The password is
-- encrypted with ERP_SETTINGS_ENC_KEY and never returned. enabled = false (or no row) = the platform SMTP is used.
CREATE TABLE company_email_setting
(
    company_id   BIGINT       NOT NULL PRIMARY KEY,
    enabled      BIT(1)       NOT NULL DEFAULT 1,
    host         VARCHAR(255) NOT NULL,
    port         INT          NOT NULL,
    username     VARCHAR(255) NULL,
    password_enc VARCHAR(1000) NULL,
    encryption   VARCHAR(10)  NOT NULL,
    from_address VARCHAR(255) NOT NULL,
    from_name    VARCHAR(100) NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL
);
