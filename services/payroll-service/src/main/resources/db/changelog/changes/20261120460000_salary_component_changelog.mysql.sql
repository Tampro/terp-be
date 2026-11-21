-- liquibase formatted sql

-- changeset ERP-PAYROLL:20261120460000-1
-- Salary components (docs/analysis/06 8.1.4): earnings and deductions of the company, a fixed amount (company
-- currency) or a percentage of the basic salary. Names unique per company (case-insensitive).
CREATE TABLE salary_component
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    name         VARCHAR(150)   NOT NULL,
    description  VARCHAR(500)   NULL,
    type         VARCHAR(10)    NOT NULL,
    calculation  VARCHAR(12)    NOT NULL,
    value        DECIMAL(15, 2) NOT NULL,
    status       VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    deleted      BIT(1)         NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_by   VARCHAR(64)    NULL,
    updated_date DATETIME(6)    NULL,
    INDEX idx_salary_component_company (company_id)
);
