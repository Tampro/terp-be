-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120160000-1
-- Holiday categories (docs/analysis/06 §6.3.4): company_id null = system category, read-only; companies add their own.
-- color is the calendar legend colour (#RRGGBB).
CREATE TABLE holiday_category
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NULL,
    name         VARCHAR(100) NOT NULL,
    color        CHAR(7)      NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_holiday_category_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-EMPLOYEE:20261120160000-2
-- One row per occurrence (no yearly repetition). No branch rows = every branch of the company.
CREATE TABLE holiday
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(150) NOT NULL,
    category_id  BIGINT       NOT NULL,
    start_date   DATE         NOT NULL,
    end_date     DATE         NOT NULL,
    description  VARCHAR(500) NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_holiday_company_dates (company_id, start_date, end_date),
    INDEX idx_holiday_category (category_id),
    CONSTRAINT fk_holiday_category FOREIGN KEY (category_id) REFERENCES holiday_category (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-EMPLOYEE:20261120160000-3
CREATE TABLE holiday_branch
(
    holiday_id BIGINT NOT NULL,
    branch_id  BIGINT NOT NULL,
    PRIMARY KEY (holiday_id, branch_id),
    INDEX idx_holiday_branch_branch (branch_id),
    CONSTRAINT fk_holiday_branch_holiday FOREIGN KEY (holiday_id) REFERENCES holiday (id),
    CONSTRAINT fk_holiday_branch_branch FOREIGN KEY (branch_id) REFERENCES branch (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
