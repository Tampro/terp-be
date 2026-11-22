-- liquibase formatted sql

-- changeset ERP-DOCUMENT:20261122740000-1
-- FR-ERP-DOC-001: HR documents follow Pending -> Approved / Rejected. The status column (VARCHAR(12)) already fits
-- PENDING and REJECTED; these columns record who decided, when, and the optional comment.
ALTER TABLE hr_document
    ADD COLUMN decided_by       VARCHAR(64)   NULL,
    ADD COLUMN decided_by_name  VARCHAR(200)  NULL,
    ADD COLUMN decided_at       DATETIME(6)   NULL,
    ADD COLUMN decision_comment VARCHAR(1000) NULL;
