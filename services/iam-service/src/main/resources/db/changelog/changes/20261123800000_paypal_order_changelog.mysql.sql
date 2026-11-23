-- liquibase formatted sql

-- changeset ERP-IAM:20261123800000-1
-- PayPal order of a plan order paid with PayPal (manual §4.2–4.3 payment method), captured when the payer returns.
ALTER TABLE plan_order
    ADD COLUMN paypal_order_id VARCHAR(64) NULL,
    ADD CONSTRAINT uk_plan_order_paypal_order UNIQUE (paypal_order_id);
