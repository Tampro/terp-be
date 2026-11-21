-- liquibase formatted sql

-- changeset ERP-ATTENDANCE:20261120200000-1
-- Shifts (docs/analysis/06 §6.4.5, columns from doc 03 `shifts`). company_id is the IAM company id (another
-- service's database: no foreign key). A night shift may end on the next day (end_time <= start_time).
CREATE TABLE shift
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id             BIGINT       NOT NULL,
    name                   VARCHAR(150) NOT NULL,
    description            VARCHAR(500) NULL,
    start_time             TIME         NOT NULL,
    end_time               TIME         NOT NULL,
    break_duration_minutes INT          NOT NULL DEFAULT 0,
    break_start_time       TIME         NULL,
    break_end_time         TIME         NULL,
    grace_period_minutes   INT          NOT NULL DEFAULT 0,
    night_shift            BIT(1)       NOT NULL DEFAULT 0,
    status                 VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    deleted                BIT(1)       NOT NULL DEFAULT 0,
    created_by             VARCHAR(64)  NULL,
    updated_by             VARCHAR(64)  NULL,
    created_date           DATETIME(6)  NULL,
    updated_date           DATETIME(6)  NULL,
    INDEX idx_shift_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ATTENDANCE:20261120200000-2
-- Attendance policies (docs/analysis/06 §6.4.6, columns from doc 03 `attendance_policies`). half_day_threshold_hours:
-- fewer worked hours than this make a Half Day (used by the attendance records).
CREATE TABLE attendance_policy
(
    id                            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id                    BIGINT        NOT NULL,
    name                          VARCHAR(150)  NOT NULL,
    description                   VARCHAR(500)  NULL,
    late_arrival_grace_minutes    INT           NOT NULL DEFAULT 15,
    early_departure_grace_minutes INT           NOT NULL DEFAULT 15,
    half_day_threshold_hours      DECIMAL(5, 2) NOT NULL DEFAULT 4.00,
    overtime_rate_per_hour        DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status                        VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    deleted                       BIT(1)        NOT NULL DEFAULT 0,
    created_by                    VARCHAR(64)   NULL,
    updated_by                    VARCHAR(64)   NULL,
    created_date                  DATETIME(6)   NULL,
    updated_date                  DATETIME(6)   NULL,
    INDEX idx_attendance_policy_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
