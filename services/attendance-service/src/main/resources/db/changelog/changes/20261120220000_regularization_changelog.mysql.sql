-- liquibase formatted sql

-- changeset ERP-ATTENDANCE:20261120220000-1
-- Attendance regularizations (docs/analysis/06 §6.4.4, columns from doc 03 `attendance_regularizations`): a request to
-- correct the clock times of an existing record. original_* are the record's times when the request was made; approving
-- writes requested_* into the record. Deleted permanently (spec), so no deleted flag.
CREATE TABLE attendance_regularization
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT        NOT NULL,
    employee_id          BIGINT        NOT NULL,
    attendance_record_id BIGINT        NOT NULL,
    work_date            DATE          NOT NULL,
    requested_clock_in   TIME          NULL,
    requested_clock_out  TIME          NULL,
    original_clock_in    TIME          NULL,
    original_clock_out   TIME          NULL,
    reason               VARCHAR(1000) NOT NULL,
    status               VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    manager_comments     VARCHAR(1000) NULL,
    decided_by           VARCHAR(64)   NULL,
    decided_at           DATETIME(6)   NULL,
    created_by           VARCHAR(64)   NULL,
    updated_by           VARCHAR(64)   NULL,
    created_date         DATETIME(6)   NULL,
    updated_date         DATETIME(6)   NULL,
    INDEX idx_regularization_company_date (company_id, work_date),
    INDEX idx_regularization_record (attendance_record_id),
    CONSTRAINT fk_regularization_record FOREIGN KEY (attendance_record_id) REFERENCES attendance_record (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
