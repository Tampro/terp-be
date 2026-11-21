-- liquibase formatted sql

-- changeset ERP-IAM:20261121290000-1
-- Stripe Checkout Session of a plan order paid with Stripe (docs/analysis/06 §3.3.2 Payment Settings).
ALTER TABLE plan_order
    ADD COLUMN stripe_session_id VARCHAR(255) NULL,
    ADD CONSTRAINT uk_plan_order_stripe_session UNIQUE (stripe_session_id);
