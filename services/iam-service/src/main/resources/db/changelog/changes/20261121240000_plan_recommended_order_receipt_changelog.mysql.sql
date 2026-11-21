-- liquibase formatted sql

-- changeset ERP-IAM:20261121240000-1
-- "Recommended" badge of a plan card (docs/analysis/06 §10.2.1).
ALTER TABLE plan
    ADD COLUMN recommended BIT(1) NOT NULL DEFAULT 0;

-- changeset ERP-IAM:20261121240000-2
-- Receipt of a plan order (§10.2.3): proof of the bank transfer, a media file of the company.
ALTER TABLE plan_order
    ADD COLUMN receipt_file_id BIGINT NULL;
