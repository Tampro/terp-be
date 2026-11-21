-- liquibase formatted sql

-- changeset ERP-IAM:20261119160000-1
-- Coupons page (docs/analysis/06 §3.2.4): maximum spend and how the code was made. Existing coupons: MANUAL.
ALTER TABLE coupon
    ADD COLUMN maximum_spend DECIMAL(15, 2) NULL,
    ADD COLUMN code_type     VARCHAR(10)    NOT NULL DEFAULT 'MANUAL';

-- changeset ERP-IAM:20261119160000-2
CREATE INDEX idx_coupon_created ON coupon (deleted, created_date);
