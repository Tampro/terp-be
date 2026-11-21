-- liquibase formatted sql

-- changeset ERP-IAM:20261121260000-1
-- A company's own System / Brand / Currency settings (docs/analysis/06 §10.5.1-10.5.3): JSON of the fields it
-- overrides; a missing field uses the platform's value (PO 2026-11-21).
CREATE TABLE company_setting
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT      NOT NULL,
    section      VARCHAR(20) NOT NULL,
    value_json   TEXT        NOT NULL,
    created_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL,
    CONSTRAINT uk_company_setting UNIQUE (company_id, section)
);
