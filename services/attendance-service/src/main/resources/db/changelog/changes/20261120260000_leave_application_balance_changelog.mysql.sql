-- liquibase formatted sql

-- changeset ERP-ATTENDANCE:20261120260000-1
-- Leave applications (docs/analysis/06 §6.5.1, columns from doc 03 `leave_applications`). total_days = working days
-- of the range (PO, 2026-11-19: days off and branch holidays excluded). attachment_file_id = media-service file.
CREATE TABLE leave_application
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id         BIGINT        NOT NULL,
    employee_id        BIGINT        NOT NULL,
    leave_type_id      BIGINT        NOT NULL,
    leave_policy_id    BIGINT        NULL,
    start_date         DATE          NOT NULL,
    end_date           DATE          NOT NULL,
    total_days         INT           NOT NULL,
    reason             VARCHAR(1000) NOT NULL,
    attachment_file_id BIGINT        NULL,
    status             VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    manager_comments   VARCHAR(1000) NULL,
    decided_by         VARCHAR(64)   NULL,
    decided_at         DATETIME(6)   NULL,
    created_by         VARCHAR(64)   NULL,
    updated_by         VARCHAR(64)   NULL,
    created_date       DATETIME(6)   NULL,
    updated_date       DATETIME(6)   NULL,
    INDEX idx_leave_application_company_dates (company_id, start_date, end_date),
    INDEX idx_leave_application_employee (employee_id, start_date),
    CONSTRAINT fk_leave_application_type FOREIGN KEY (leave_type_id) REFERENCES leave_type (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ATTENDANCE:20261120260000-2
-- Leave balances (docs/analysis/06 §6.5.2, doc 03 `leave_balances`): one row per employee, leave type and year,
-- written by Sync. Available = allocated + carried forward + manual adjustment - used.
CREATE TABLE leave_balance
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT        NOT NULL,
    employee_id       BIGINT        NOT NULL,
    leave_type_id     BIGINT        NOT NULL,
    leave_policy_id   BIGINT        NULL,
    balance_year      INT           NOT NULL,
    allocated_days    DECIMAL(8, 2) NOT NULL DEFAULT 0,
    used_days         DECIMAL(8, 2) NOT NULL DEFAULT 0,
    carried_forward   DECIMAL(8, 2) NOT NULL DEFAULT 0,
    manual_adjustment DECIMAL(8, 2) NOT NULL DEFAULT 0,
    adjustment_reason VARCHAR(500)  NULL,
    created_by        VARCHAR(64)   NULL,
    updated_by        VARCHAR(64)   NULL,
    created_date      DATETIME(6)   NULL,
    updated_date      DATETIME(6)   NULL,
    UNIQUE KEY uk_leave_balance (employee_id, leave_type_id, balance_year),
    INDEX idx_leave_balance_company_year (company_id, balance_year),
    CONSTRAINT fk_leave_balance_type FOREIGN KEY (leave_type_id) REFERENCES leave_type (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ATTENDANCE:20261120260000-3
-- Sync history (doc 03 `leave_balance_syncs`). synced_by = who pressed Sync (username).
CREATE TABLE leave_balance_sync
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT      NOT NULL,
    sync_year    INT         NOT NULL,
    synced_at    DATETIME(6) NOT NULL,
    synced_by    VARCHAR(150) NULL,
    employees    INT         NOT NULL,
    balances     INT         NOT NULL,
    created_by   VARCHAR(64) NULL,
    updated_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_date DATETIME(6) NULL,
    INDEX idx_leave_balance_sync_company (company_id, synced_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
