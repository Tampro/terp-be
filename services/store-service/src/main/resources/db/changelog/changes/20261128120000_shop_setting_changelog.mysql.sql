-- liquibase formatted sql

-- changeset ERP-STORE:20261128120000-1
-- One public shop per company: its address (slug, unique across all companies) and whether it is open to customers.
CREATE TABLE shop_setting
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT      NOT NULL,
    slug         VARCHAR(40) NOT NULL,
    enabled      BIT(1)      NOT NULL DEFAULT 0,
    created_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL,
    UNIQUE KEY uk_shop_setting_company (company_id),
    UNIQUE KEY uk_shop_setting_slug (slug)
);
