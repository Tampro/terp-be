-- liquibase formatted sql

-- changeset ERP-PAYROLL:20261120480000-1
-- Payroll runs (docs/analysis/06 8.1.2): a pay cycle of the company. Totals are copied from the entries when the run
-- is processed (and when an entry is removed) so the list needs no aggregate query.
CREATE TABLE payroll_run
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT         NOT NULL,
    title            VARCHAR(200)   NOT NULL,
    frequency        VARCHAR(20)    NOT NULL,
    period_start     DATE           NOT NULL,
    period_end       DATE           NOT NULL,
    pay_date         DATE           NOT NULL,
    notes            VARCHAR(1000)  NULL,
    status           VARCHAR(12)    NOT NULL DEFAULT 'DRAFT',
    employee_count   INT            NOT NULL DEFAULT 0,
    gross_pay        DECIMAL(15, 2) NOT NULL DEFAULT 0,
    total_deductions DECIMAL(15, 2) NOT NULL DEFAULT 0,
    net_pay          DECIMAL(15, 2) NOT NULL DEFAULT 0,
    processed_at     DATETIME(6)    NULL,
    deleted          BIT(1)         NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)    NULL,
    created_date     DATETIME(6)    NULL,
    updated_by       VARCHAR(64)    NULL,
    updated_date     DATETIME(6)    NULL,
    INDEX idx_payroll_run_company (company_id)
);

-- changeset ERP-PAYROLL:20261120480000-2
-- One employee of a processed run: a snapshot of the salary structure and the attendance of the period, so later
-- changes to either never alter a processed run.
CREATE TABLE payroll_entry
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id                 BIGINT         NOT NULL,
    company_id             BIGINT         NOT NULL,
    employee_id            BIGINT         NOT NULL,
    employee_name          VARCHAR(200)   NULL,
    employee_no            VARCHAR(50)    NULL,
    employee_email         VARCHAR(255)   NULL,
    salary_id              BIGINT         NOT NULL,
    basic_salary           DECIMAL(15, 2) NOT NULL,
    working_days           INT            NOT NULL,
    present_days           DECIMAL(5, 1)  NOT NULL,
    paid_leave_days        DECIMAL(5, 1)  NOT NULL,
    unpaid_leave_days      DECIMAL(5, 1)  NOT NULL,
    lop_days               DECIMAL(5, 1)  NOT NULL,
    overtime_hours         DECIMAL(7, 2)  NOT NULL,
    total_earnings         DECIMAL(15, 2) NOT NULL,
    lop_deduction          DECIMAL(15, 2) NOT NULL,
    unpaid_leave_deduction DECIMAL(15, 2) NOT NULL,
    overtime_amount        DECIMAL(15, 2) NOT NULL,
    gross_pay              DECIMAL(15, 2) NOT NULL,
    total_deductions       DECIMAL(15, 2) NOT NULL,
    net_pay                DECIMAL(15, 2) NOT NULL,
    created_by             VARCHAR(64)    NULL,
    created_date           DATETIME(6)    NULL,
    updated_by             VARCHAR(64)    NULL,
    updated_date           DATETIME(6)    NULL,
    UNIQUE KEY uk_payroll_entry_run_employee (run_id, employee_id),
    INDEX idx_payroll_entry_employee (employee_id),
    CONSTRAINT fk_payroll_entry_run FOREIGN KEY (run_id) REFERENCES payroll_run (id)
);

-- changeset ERP-PAYROLL:20261120480000-3
-- The components of an entry as they were when processed (name and type copied: a component may be renamed later).
CREATE TABLE payroll_entry_line
(
    entry_id     BIGINT         NOT NULL,
    component_id BIGINT         NOT NULL,
    name         VARCHAR(150)   NOT NULL,
    type         VARCHAR(10)    NOT NULL,
    calculation  VARCHAR(12)    NOT NULL,
    value        DECIMAL(15, 2) NOT NULL,
    amount       DECIMAL(15, 2) NOT NULL,
    PRIMARY KEY (entry_id, component_id),
    CONSTRAINT fk_payroll_entry_line_entry FOREIGN KEY (entry_id) REFERENCES payroll_entry (id)
);
