-- liquibase formatted sql

-- changeset ERP-PAYROLL:20261122710000-1
-- Setup catalogues of manual 15: loan, allowance and deduction types, told apart by kind. Code unique per company and
-- kind among the rows not deleted (checked by the service: a soft-deleted code may be reused).
CREATE TABLE payroll_type
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    kind         VARCHAR(12)  NOT NULL,
    name         VARCHAR(150) NOT NULL,
    code         VARCHAR(50)  NOT NULL,
    active       BIT(1)       NOT NULL DEFAULT 1,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_payroll_type_company_kind (company_id, kind)
);

-- changeset ERP-PAYROLL:20261122710000-2
-- Loans to employees, repaid by an installment per payroll run while ACTIVE and overlapping the run period.
CREATE TABLE employee_loan
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id         BIGINT         NOT NULL,
    employee_id        BIGINT         NOT NULL,
    loan_type_id       BIGINT         NOT NULL,
    title              VARCHAR(200)   NOT NULL,
    amount             DECIMAL(19, 4) NOT NULL,
    installment_amount DECIMAL(19, 4) NOT NULL,
    installments       INT            NULL,
    start_date         DATE           NOT NULL,
    end_date           DATE           NULL,
    reason             VARCHAR(1000)  NULL,
    status             VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    remaining_balance  DECIMAL(19, 4) NOT NULL,
    deleted            BIT(1)         NOT NULL DEFAULT 0,
    created_by         VARCHAR(64)    NULL,
    created_date       DATETIME(6)    NULL,
    updated_by         VARCHAR(64)    NULL,
    updated_date       DATETIME(6)    NULL,
    INDEX idx_employee_loan_company_employee (company_id, employee_id),
    INDEX idx_employee_loan_type (loan_type_id)
);

-- changeset ERP-PAYROLL:20261122710000-3
-- The installment one processed run deducted from a loan; deleted when the run is cancelled or the entry removed.
CREATE TABLE loan_repayment
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    loan_id      BIGINT         NOT NULL,
    run_id       BIGINT         NOT NULL,
    employee_id  BIGINT         NOT NULL,
    amount       DECIMAL(19, 4) NOT NULL,
    created_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_by   VARCHAR(64)    NULL,
    updated_date DATETIME(6)    NULL,
    UNIQUE KEY uk_loan_repayment_loan_run (loan_id, run_id),
    INDEX idx_loan_repayment_run (run_id, employee_id),
    CONSTRAINT fk_loan_repayment_loan FOREIGN KEY (loan_id) REFERENCES employee_loan (id),
    CONSTRAINT fk_loan_repayment_run FOREIGN KEY (run_id) REFERENCES payroll_run (id)
);

-- changeset ERP-PAYROLL:20261122710000-4
-- Manual overtime (apart from attendance overtime); only APPROVED is paid, once: paid_run_id = the run that paid it.
CREATE TABLE employee_overtime
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    employee_id  BIGINT         NOT NULL,
    title        VARCHAR(200)   NOT NULL,
    days         DECIMAL(7, 2)  NOT NULL,
    hours        DECIMAL(7, 2)  NOT NULL,
    rate         DECIMAL(19, 4) NOT NULL,
    start_date   DATE           NOT NULL,
    end_date     DATE           NOT NULL,
    status       VARCHAR(10)    NOT NULL DEFAULT 'PENDING',
    notes        VARCHAR(1000)  NULL,
    paid_run_id  BIGINT         NULL,
    deleted      BIT(1)         NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_by   VARCHAR(64)    NULL,
    updated_date DATETIME(6)    NULL,
    INDEX idx_employee_overtime_company_employee (company_id, employee_id),
    INDEX idx_employee_overtime_paid_run (paid_run_id)
);

-- changeset ERP-PAYROLL:20261122710000-5
-- Employees left out of a DRAFT run.
CREATE TABLE payroll_run_exclusion
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id       BIGINT      NOT NULL,
    company_id   BIGINT      NOT NULL,
    employee_id  BIGINT      NOT NULL,
    created_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL,
    UNIQUE KEY uk_payroll_run_exclusion (run_id, employee_id),
    CONSTRAINT fk_payroll_run_exclusion_run FOREIGN KEY (run_id) REFERENCES payroll_run (id)
);

-- changeset ERP-PAYROLL:20261122710000-6
-- Pay salary: the bank account (accounting-service id) and the payment state of a run.
ALTER TABLE payroll_run
    ADD COLUMN bank_account_id BIGINT      NULL,
    ADD COLUMN payment_status  VARCHAR(8)  NOT NULL DEFAULT 'UNPAID',
    ADD COLUMN paid_at         DATETIME(6) NULL,
    ADD COLUMN paid_by         VARCHAR(64) NULL;

-- changeset ERP-PAYROLL:20261122710000-7
-- Manual overtime and loan deductions of an entry; 0 for entries processed before they existed.
ALTER TABLE payroll_entry
    ADD COLUMN manual_overtime_amount DECIMAL(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN loan_deduction         DECIMAL(15, 2) NOT NULL DEFAULT 0;
