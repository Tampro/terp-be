-- liquibase formatted sql

-- changeset ERP-IAM:20261121170000-1
-- IP Restriction (docs/analysis/06 10.5.6; PO 2026-11-21): per company, enforced at login and token refresh when the
-- company switch and the platform switch (System Settings) are on and the list is not empty.
CREATE TABLE company_ip_restriction
(
    company_id   BIGINT      NOT NULL PRIMARY KEY,
    enabled      BIT(1)      NOT NULL DEFAULT 0,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL
);

-- changeset ERP-IAM:20261121170000-2
-- An allowed address: one IPv4 / IPv6 address or a CIDR range, normalized; unique per company.
CREATE TABLE company_allowed_ip
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    ip_address   VARCHAR(64)  NOT NULL,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    UNIQUE KEY uk_company_allowed_ip (company_id, ip_address)
);
