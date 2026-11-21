-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120140000-1
-- Departments page (docs/analysis/06 §6.3.2): description and status. Existing departments become ACTIVE.
ALTER TABLE department
    ADD COLUMN description VARCHAR(500) NULL AFTER name,
    ADD COLUMN status      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' AFTER description;
