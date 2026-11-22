-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261122730000-1
-- IN_PROGRESS (11 chars) is a transfer status now (FR-ERP-CAR-001).
ALTER TABLE employee_transfer
    MODIFY COLUMN status VARCHAR(12) NOT NULL DEFAULT 'PENDING';

-- changeset ERP-EMPLOYEE:20261122730000-2
-- New initial statuses of the manual §15 (warning, termination, complaint).
ALTER TABLE employee_warning
    ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE employee_termination
    ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE employee_complaint
    ALTER COLUMN status SET DEFAULT 'PENDING';

-- changeset ERP-EMPLOYEE:20261122730000-3
-- A promotion may move the employee to another branch / department (FR-ERP-REC-001); null on older rows.
ALTER TABLE employee_promotion
    ADD COLUMN previous_branch_id     BIGINT NULL AFTER employee_id,
    ADD COLUMN previous_department_id BIGINT NULL AFTER previous_branch_id,
    ADD COLUMN new_branch_id          BIGINT NULL AFTER previous_designation,
    ADD COLUMN new_department_id      BIGINT NULL AFTER new_branch_id;

-- changeset ERP-EMPLOYEE:20261122730000-4
-- Hours & Rates step of the employee form (FR-ERP-EMP-001).
ALTER TABLE employee
    ADD COLUMN hours_per_day DECIMAL(5, 2)  NULL AFTER base_salary,
    ADD COLUMN days_per_week INT            NULL AFTER hours_per_day,
    ADD COLUMN rate_per_hour DECIMAL(19, 4) NULL AFTER days_per_week;

-- changeset ERP-EMPLOYEE:20261122730000-5
-- Is Paid (FR-ERP-HOL-001); existing holidays are paid.
ALTER TABLE holiday
    ADD COLUMN is_paid BIT(1) NOT NULL DEFAULT 1 AFTER description;

-- changeset ERP-EMPLOYEE:20261122730000-6
-- Stored Draft / Active / Inactive (FR-ERP-ANN-001); existing announcements were all published, so ACTIVE.
ALTER TABLE announcement
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' AFTER company_wide,
    ADD INDEX idx_announcement_company_status (company_id, status);
