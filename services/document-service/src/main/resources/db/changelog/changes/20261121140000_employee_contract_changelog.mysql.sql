-- liquibase formatted sql

-- changeset ERP-DOCUMENT:20261121140000-1
-- Employee contracts (docs/analysis/06 9.4.2). contract_number is derived from the id after insert (CT-yyyy-000123),
-- unique per company; the employee lives in employee-service (name / no. / email copied for search). An ACTIVE contract
-- past its end date reads as EXPIRED.
CREATE TABLE employee_contract
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT         NOT NULL,
    contract_number      VARCHAR(30)    NULL,
    contract_type_id     BIGINT         NOT NULL,
    employee_id          BIGINT         NOT NULL,
    employee_name        VARCHAR(200)   NULL,
    employee_no          VARCHAR(50)    NULL,
    employee_email       VARCHAR(255)   NULL,
    start_date           DATE           NOT NULL,
    end_date             DATE           NULL,
    basic_salary         DECIMAL(15, 2) NOT NULL DEFAULT 0,
    terms_and_conditions TEXT           NULL,
    status               VARCHAR(12)    NOT NULL DEFAULT 'DRAFT',
    deleted              BIT(1)         NOT NULL DEFAULT 0,
    created_by           VARCHAR(64)    NULL,
    created_date         DATETIME(6)    NULL,
    updated_by           VARCHAR(64)    NULL,
    updated_date         DATETIME(6)    NULL,
    UNIQUE KEY uk_employee_contract_number (company_id, contract_number),
    INDEX idx_employee_contract_company (company_id, status, end_date),
    INDEX idx_employee_contract_employee (employee_id),
    INDEX idx_employee_contract_type (contract_type_id),
    CONSTRAINT fk_employee_contract_type FOREIGN KEY (contract_type_id) REFERENCES contract_type (id)
);
