-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120190100-1
-- Descriptions of the system document types. Idempotent. System types default to Optional; each company decides.
-- TODO(assumption): default Optional for every system type — confirm with BA.
UPDATE document_type SET description = 'Government-issued ID: citizen ID card or passport'
WHERE id = 1 AND company_id IS NULL;
UPDATE document_type SET description = 'Proof of residence, e.g. household registration or a utility bill'
WHERE id = 2 AND company_id IS NULL;
UPDATE document_type SET description = 'Diplomas, degrees and transcripts'
WHERE id = 3 AND company_id IS NULL;
UPDATE document_type SET description = 'Letters from previous employers'
WHERE id = 4 AND company_id IS NULL;
UPDATE document_type SET description = 'Health check results and medical certificates'
WHERE id = 5 AND company_id IS NULL;
