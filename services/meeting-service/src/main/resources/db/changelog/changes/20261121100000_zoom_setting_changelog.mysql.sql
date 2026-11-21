-- liquibase formatted sql

-- changeset ERP-MEETING:20261121100000-1
-- Zoom Server-to-Server OAuth credentials of a company (docs/analysis/06 9.3, PO 2026-11-21). The client secret is
-- AES-256-GCM encrypted with MEETING_ENC_KEY and never returned by the API; verified = the last Test Connection
-- succeeded with these credentials (it turns the Zoom Meeting menu on).
CREATE TABLE zoom_setting
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT        NOT NULL,
    account_id           VARCHAR(100)  NOT NULL,
    client_id            VARCHAR(100)  NOT NULL,
    client_secret_cipher VARCHAR(500)  NOT NULL,
    verified             BIT(1)        NOT NULL DEFAULT 0,
    verified_at          DATETIME(6)   NULL,
    created_by           VARCHAR(64)   NULL,
    created_date         DATETIME(6)   NULL,
    updated_by           VARCHAR(64)   NULL,
    updated_date         DATETIME(6)   NULL,
    UNIQUE KEY uk_zoom_setting_company (company_id)
);
