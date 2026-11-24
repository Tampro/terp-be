-- liquibase formatted sql

-- changeset ERP-IAM:20261125200000-1
-- Retention (PO 2026-11-24): a deleted company is kept 5 years from deleted_at, then anonymised (anonymized_at).
ALTER TABLE company
    ADD COLUMN deleted_at    DATETIME(6) NULL,
    ADD COLUMN anonymized_at DATETIME(6) NULL;
-- rollback ALTER TABLE company DROP COLUMN deleted_at, DROP COLUMN anonymized_at;

-- changeset ERP-IAM:20261125200000-2
-- Companies deleted before the column existed: their last update is the deletion.
UPDATE company SET deleted_at = COALESCE(updated_date, created_date) WHERE deleted = 1 AND deleted_at IS NULL;
-- rollback SELECT 1;
