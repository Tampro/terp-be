-- liquibase formatted sql

-- changeset ERP-ACCOUNTING:20261121600000-1
-- Vendors (FR-ACC-VEN-001, same shape as customer). vendor_code generated per company from
-- vendor_code_sequence; user_id = optional ERP account of a user, unique per company among not-deleted rows
-- (checked by the service, a soft-deleted row keeps its value).
CREATE TABLE vendor
(
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id               BIGINT       NOT NULL,
    vendor_code              VARCHAR(20)  NOT NULL,
    company_name             VARCHAR(255) NOT NULL,
    contact_person           VARCHAR(255) NOT NULL,
    email                    VARCHAR(255) NOT NULL,
    mobile                   VARCHAR(30)  NULL,
    tax_number               VARCHAR(50)  NULL,
    payment_terms            VARCHAR(100) NULL,
    user_id                  BIGINT       NULL,
    billing_name             VARCHAR(255) NULL,
    billing_address          VARCHAR(255) NULL,
    billing_address2         VARCHAR(255) NULL,
    billing_city             VARCHAR(100) NULL,
    billing_state            VARCHAR(100) NULL,
    billing_country          VARCHAR(100) NULL,
    billing_zip              VARCHAR(20)  NULL,
    shipping_same_as_billing BIT(1)       NOT NULL DEFAULT 0,
    shipping_name            VARCHAR(255) NULL,
    shipping_address         VARCHAR(255) NULL,
    shipping_address2        VARCHAR(255) NULL,
    shipping_city            VARCHAR(100) NULL,
    shipping_state           VARCHAR(100) NULL,
    shipping_country         VARCHAR(100) NULL,
    shipping_zip             VARCHAR(20)  NULL,
    notes                    TEXT         NULL,
    deleted                  BIT(1)       NOT NULL DEFAULT 0,
    created_by               VARCHAR(64)  NULL,
    created_date             DATETIME(6)  NULL,
    updated_by               VARCHAR(64)  NULL,
    updated_date             DATETIME(6)  NULL,
    -- Codes are never reused, so uniqueness holds across deleted rows too.
    UNIQUE KEY uk_vendor_company_code (company_id, vendor_code),
    INDEX idx_vendor_company (company_id),
    INDEX idx_vendor_user (company_id, user_id)
);

-- changeset ERP-ACCOUNTING:20261121600000-2
-- Last vendor number issued per company; the row is locked FOR UPDATE while a code is issued.
CREATE TABLE vendor_code_sequence
(
    company_id BIGINT NOT NULL PRIMARY KEY,
    last_number BIGINT NOT NULL
);
