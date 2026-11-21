-- liquibase formatted sql

-- changeset ERP-IAM:20261121280000-1
-- Login as Company (docs/analysis/06 §3.2.1, PO 2026-11-21): a super admin acts as a company's administrator for a
-- limited time; every session is kept for audit. Only the SHA-256 of the session key is stored.
CREATE TABLE impersonation_session
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    key_hash             CHAR(64)     NOT NULL,
    super_admin_sub      VARCHAR(64)  NOT NULL,
    super_admin_username VARCHAR(100) NULL,
    company_id           BIGINT       NOT NULL,
    client_ip            VARCHAR(64)  NULL,
    expires_at           DATETIME(6)  NOT NULL,
    ended_at             DATETIME(6)  NULL,
    created_by           VARCHAR(64)  NULL,
    created_date         DATETIME(6)  NULL,
    updated_by           VARCHAR(64)  NULL,
    updated_date         DATETIME(6)  NULL,
    CONSTRAINT uk_impersonation_key UNIQUE (key_hash),
    INDEX idx_impersonation_company (company_id, created_date)
);
