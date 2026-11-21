-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120130000-1
-- Branches page (docs/analysis/06 §6.3.1): address, contact and status. Existing branches become ACTIVE.
ALTER TABLE branch
    ADD COLUMN address     VARCHAR(255) NULL AFTER name,
    ADD COLUMN city        VARCHAR(100) NULL AFTER address,
    ADD COLUMN state       VARCHAR(100) NULL AFTER city,
    ADD COLUMN country     VARCHAR(100) NULL AFTER state,
    ADD COLUMN postal_code VARCHAR(20)  NULL AFTER country,
    ADD COLUMN phone       VARCHAR(30)  NULL AFTER postal_code,
    ADD COLUMN email       VARCHAR(255) NULL AFTER phone,
    ADD COLUMN status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' AFTER email;
