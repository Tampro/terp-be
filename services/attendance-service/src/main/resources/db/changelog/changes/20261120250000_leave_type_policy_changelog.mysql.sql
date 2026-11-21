-- liquibase formatted sql

-- changeset ERP-ATTENDANCE:20261120250000-1
-- Leave types of a company (docs/analysis/06 §6.5.3, columns from doc 03 `leave_types`). No seed: days per year and
-- paid/unpaid differ per company and by labour law (PO, 2026-11-19).
CREATE TABLE leave_type
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT       NOT NULL,
    name              VARCHAR(150) NOT NULL,
    description       VARCHAR(1000) NULL,
    max_days_per_year INT          NOT NULL DEFAULT 0,
    paid              BIT(1)       NOT NULL DEFAULT 1,
    color             CHAR(7)      NOT NULL DEFAULT '#3B82F6',
    status            VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    deleted           BIT(1)       NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)  NULL,
    updated_by        VARCHAR(64)  NULL,
    created_date      DATETIME(6)  NULL,
    updated_date      DATETIME(6)  NULL,
    INDEX idx_leave_type_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ATTENDANCE:20261120250000-2
-- Leave policies (docs/analysis/06 §6.5.4, doc 03 `leave_policies`): the rules of one leave type. doc 03's
-- accrual_type / accrual_rate are not in the spec's form and are left out until a page needs them.
CREATE TABLE leave_policy
(
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id               BIGINT        NOT NULL,
    name                     VARCHAR(150)  NOT NULL,
    description              VARCHAR(1000) NULL,
    leave_type_id            BIGINT        NOT NULL,
    carry_forward_limit      INT           NOT NULL DEFAULT 0,
    min_days_per_application INT           NOT NULL DEFAULT 1,
    max_days_per_application INT           NOT NULL DEFAULT 30,
    requires_approval        BIT(1)        NOT NULL DEFAULT 1,
    status                   VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    deleted                  BIT(1)        NOT NULL DEFAULT 0,
    created_by               VARCHAR(64)   NULL,
    updated_by               VARCHAR(64)   NULL,
    created_date             DATETIME(6)   NULL,
    updated_date             DATETIME(6)   NULL,
    INDEX idx_leave_policy_company (company_id),
    INDEX idx_leave_policy_type (leave_type_id),
    CONSTRAINT fk_leave_policy_type FOREIGN KEY (leave_type_id) REFERENCES leave_type (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
