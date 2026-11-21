-- liquibase formatted sql

-- changeset ERP-IAM:20261121250000-1
-- "Start Trial" of a plan by a company (docs/analysis/06 §10.2.1): once per company and plan (PO 2026-11-21).
CREATE TABLE company_plan_trial
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT      NOT NULL,
    plan_id      BIGINT      NOT NULL,
    ends_at      DATETIME(6) NOT NULL,
    created_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL,
    CONSTRAINT uk_company_plan_trial UNIQUE (company_id, plan_id)
);
