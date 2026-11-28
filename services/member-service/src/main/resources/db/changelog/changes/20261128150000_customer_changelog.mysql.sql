-- liquibase formatted sql

-- changeset ERP-MEMBER:20261128150000-1
-- A customer's membership of one shop (company). Minimal personal data: the customer realm subject and the consents
-- (version + time, evidence for PIPA); name / email / phone stay in Keycloak. Leaving keeps the row (LEFT, left_at) as
-- consent history; re-joining is a new row. live_key = 0 while ACTIVE, the row id once LEFT, so one ACTIVE row per
-- (company, customer) is enforced by uk_customer_live.
CREATE TABLE customer
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT      NOT NULL,
    keycloak_sub        VARCHAR(64) NOT NULL,
    status              VARCHAR(10) NOT NULL,
    terms_version       VARCHAR(20) NOT NULL,
    terms_agreed_at     DATETIME(6) NOT NULL,
    privacy_version     VARCHAR(20) NOT NULL,
    privacy_agreed_at   DATETIME(6) NOT NULL,
    marketing_agreed    BIT(1)      NOT NULL DEFAULT 0,
    marketing_agreed_at DATETIME(6) NULL,
    joined_at           DATETIME(6) NOT NULL,
    left_at             DATETIME(6) NULL,
    live_key            BIGINT      NOT NULL DEFAULT 0,
    created_by          VARCHAR(64) NULL,
    created_date        DATETIME(6) NULL,
    updated_by          VARCHAR(64) NULL,
    updated_date        DATETIME(6) NULL,
    UNIQUE KEY uk_customer_live (company_id, keycloak_sub, live_key),
    INDEX idx_customer_sub (keycloak_sub, company_id, left_at)
);
