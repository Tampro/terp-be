-- liquibase formatted sql

-- changeset ERP-ORDER:20261129200000-1
-- Back Office cancel of a paid order (full refund, sandbox): when and why.
ALTER TABLE customer_order
    ADD COLUMN refunded_at DATETIME(6) NULL,
    ADD COLUMN cancel_reason VARCHAR(200) NULL;
