-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120150000-1
-- Designations page (docs/analysis/06 §6.3.3): description and status. Existing designations become ACTIVE.
ALTER TABLE designation
    ADD COLUMN description VARCHAR(500) NULL AFTER name,
    ADD COLUMN status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' AFTER description;
