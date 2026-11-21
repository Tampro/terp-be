-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120120000-1
-- Reporting line of the Organization Chart (docs/analysis/06 §6.2).
ALTER TABLE employee
    ADD COLUMN reports_to_id BIGINT NULL AFTER designation_id,
    ADD CONSTRAINT fk_employee_reports_to FOREIGN KEY (reports_to_id) REFERENCES employee (id);
