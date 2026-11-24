-- liquibase formatted sql

-- changeset ERP-IAM:20261125300000-1
-- Accounting retention (Law on Accounting 2015 Art. 41; PO 2026-11-24): finance data is anonymised 10 years after
-- the deletion; this marks that accounting and commerce were signalled.
ALTER TABLE company ADD COLUMN finance_anonymized_at DATETIME(6) NULL;
-- rollback ALTER TABLE company DROP COLUMN finance_anonymized_at;
