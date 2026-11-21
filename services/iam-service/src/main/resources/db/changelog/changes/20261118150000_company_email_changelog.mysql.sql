-- liquibase formatted sql

-- changeset ERP-IAM:20261118150000-1
-- Contact email shown in the super admin companies list; existing companies stay NULL.
ALTER TABLE company
    ADD COLUMN email VARCHAR(255) NULL;

-- changeset ERP-IAM:20261118150000-2
-- Companies list filters on created date and status.
CREATE INDEX idx_company_created ON company (deleted, created_date);
