-- liquibase formatted sql

-- changeset ERP-ATTENDANCE:20261120240000-1
-- Timesheet entries (docs/analysis/06 §6.4.2, columns from doc 03 `time_entries`): hours an employee logged against a
-- project on a day, approved or rejected one by one.
CREATE TABLE time_entry
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT        NOT NULL,
    employee_id      BIGINT        NOT NULL,
    work_date        DATE          NOT NULL,
    hours            DECIMAL(5, 2) NOT NULL,
    project          VARCHAR(150)  NULL,
    description      VARCHAR(1000) NOT NULL,
    status           VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    manager_comments VARCHAR(1000) NULL,
    decided_by       VARCHAR(64)   NULL,
    decided_at       DATETIME(6)   NULL,
    created_by       VARCHAR(64)   NULL,
    updated_by       VARCHAR(64)   NULL,
    created_date     DATETIME(6)   NULL,
    updated_date     DATETIME(6)   NULL,
    INDEX idx_time_entry_company_date (company_id, work_date),
    INDEX idx_time_entry_employee_date (employee_id, work_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
