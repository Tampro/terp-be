-- liquibase formatted sql

-- changeset ERP-IAM:20261121200000-1
-- Email verification of a company (docs/analysis/06 §2.5). email_verify_required: login refused until verified
-- (self-registered companies only, PO 2026-11-21).
ALTER TABLE company
    ADD COLUMN email_verified_at     DATETIME(6) NULL,
    ADD COLUMN email_verify_required BIT(1)      NOT NULL DEFAULT 0;

-- changeset ERP-IAM:20261121200000-2
-- Profile picture (§3 Profile Settings): a file of the company's media library.
ALTER TABLE user_account
    ADD COLUMN avatar_media_id BIGINT NULL;

-- changeset ERP-IAM:20261121200000-3
-- One-time links (password reset §2.4, email verification §2.5). Only the SHA-256 of the token is stored.
CREATE TABLE account_token
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    purpose      VARCHAR(20) NOT NULL,
    user_id      BIGINT      NULL,
    company_id   BIGINT      NULL,
    token_hash   CHAR(64)    NOT NULL,
    expires_at   DATETIME(6) NOT NULL,
    used_at      DATETIME(6) NULL,
    created_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL,
    CONSTRAINT uk_account_token_hash UNIQUE (token_hash),
    INDEX idx_account_token_user (purpose, user_id),
    INDEX idx_account_token_company (purpose, company_id)
);
