-- liquibase formatted sql

-- changeset ERP-IAM:20261121270000-1
-- Cookie consent choices of visitors (docs/analysis/06 §3.3.2 Enable Logging, Download Accepted Cookies).
-- The IP is stored shortened (IPv4 last octet, IPv6 after the 3rd group dropped) to keep personal data minimal.
CREATE TABLE cookie_consent
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    choice       VARCHAR(20)  NOT NULL,
    categories   VARCHAR(400) NOT NULL,
    ip_masked    VARCHAR(64)  NULL,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_cookie_consent_date (created_date)
);
