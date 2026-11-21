-- liquibase formatted sql

-- changeset ERP-PAYROLL:20261120490000-1
-- Payslips (docs/analysis/06 8.1.1): one per entry of a completed run. The amounts stay on the entry; the employee,
-- pay date, period and net pay are copied for the listing (month tabs, search) without joins.
CREATE TABLE payslip
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT         NOT NULL,
    run_id         BIGINT         NOT NULL,
    entry_id       BIGINT         NOT NULL,
    employee_id    BIGINT         NOT NULL,
    employee_name  VARCHAR(200)   NULL,
    employee_no    VARCHAR(50)    NULL,
    employee_email VARCHAR(255)   NULL,
    period_year    INT            NOT NULL,
    period_month   INT            NOT NULL,
    period_start   DATE           NOT NULL,
    period_end     DATE           NOT NULL,
    pay_date       DATE           NOT NULL,
    net_pay        DECIMAL(15, 2) NOT NULL,
    status         VARCHAR(12)    NOT NULL DEFAULT 'GENERATED',
    generated_at   DATETIME(6)    NOT NULL,
    downloaded_at  DATETIME(6)    NULL,
    created_by     VARCHAR(64)    NULL,
    created_date   DATETIME(6)    NULL,
    updated_by     VARCHAR(64)    NULL,
    updated_date   DATETIME(6)    NULL,
    UNIQUE KEY uk_payslip_entry (entry_id),
    INDEX idx_payslip_company_period (company_id, period_year, period_month),
    INDEX idx_payslip_run (run_id),
    CONSTRAINT fk_payslip_run FOREIGN KEY (run_id) REFERENCES payroll_run (id),
    CONSTRAINT fk_payslip_entry FOREIGN KEY (entry_id) REFERENCES payroll_entry (id)
);
