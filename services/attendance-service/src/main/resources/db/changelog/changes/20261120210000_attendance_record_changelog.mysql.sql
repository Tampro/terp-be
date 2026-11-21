-- liquibase formatted sql

-- changeset ERP-ATTENDANCE:20261120210000-1
-- Weekly days off of a company (PO, 2026-11-19: per-company setting, default Saturday and Sunday). A company without a
-- row uses the default.
CREATE TABLE attendance_setting
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT      NOT NULL,
    weekly_off_days  VARCHAR(80) NOT NULL,
    created_by       VARCHAR(64) NULL,
    updated_by       VARCHAR(64) NULL,
    created_date     DATETIME(6) NULL,
    updated_date     DATETIME(6) NULL,
    UNIQUE KEY uk_attendance_setting_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ATTENDANCE:20261120210000-2
-- Attendance records (docs/analysis/06 §6.4.1, columns from doc 03 `attendance_records`). employee_id is the
-- employee-service id (no foreign key across databases). shift_id / attendance_policy_id are copied from the employee
-- profile when the record is saved, so later profile changes do not rewrite past days. One record per employee and day.
CREATE TABLE attendance_record
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT        NOT NULL,
    employee_id          BIGINT        NOT NULL,
    work_date            DATE          NOT NULL,
    shift_id             BIGINT        NULL,
    attendance_policy_id BIGINT        NULL,
    clock_in             TIME          NULL,
    clock_out            TIME          NULL,
    total_hours          DECIMAL(5, 2) NOT NULL DEFAULT 0,
    break_hours          DECIMAL(5, 2) NOT NULL DEFAULT 0,
    overtime_hours       DECIMAL(5, 2) NOT NULL DEFAULT 0,
    overtime_amount      DECIMAL(14, 2) NOT NULL DEFAULT 0,
    is_late              BIT(1)        NOT NULL DEFAULT 0,
    is_early_departure   BIT(1)        NOT NULL DEFAULT 0,
    is_absent            BIT(1)        NOT NULL DEFAULT 0,
    is_holiday           BIT(1)        NOT NULL DEFAULT 0,
    is_weekend           BIT(1)        NOT NULL DEFAULT 0,
    status               VARCHAR(12)   NOT NULL DEFAULT 'PRESENT',
    notes                VARCHAR(500)  NULL,
    biometric_id         VARCHAR(100)  NULL,
    created_by           VARCHAR(64)   NULL,
    updated_by           VARCHAR(64)   NULL,
    created_date         DATETIME(6)   NULL,
    updated_date         DATETIME(6)   NULL,
    UNIQUE KEY uk_attendance_record_employee_date (employee_id, work_date),
    INDEX idx_attendance_record_company_date (company_id, work_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
