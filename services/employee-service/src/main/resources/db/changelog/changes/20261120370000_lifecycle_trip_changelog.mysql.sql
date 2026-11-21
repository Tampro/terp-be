-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120370000-1
-- Business trips (docs/analysis/06 7.2.7). Amounts in the company currency (PO, 2026-11-20). advance_status is set
-- once an advance is requested; reimbursement_status once the first expense is recorded; expenses_total is kept in
-- step with trip_expense so the list does not sum per row.
CREATE TABLE employee_trip
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT        NOT NULL,
    employee_id          BIGINT        NOT NULL,
    purpose              VARCHAR(200)  NOT NULL,
    destination          VARCHAR(200)  NOT NULL,
    start_date           DATE          NOT NULL,
    end_date             DATE          NOT NULL,
    description          TEXT          NULL,
    expected_outcomes    TEXT          NULL,
    document_media_id    BIGINT        NULL,
    status               VARCHAR(12)   NOT NULL DEFAULT 'PLANNED',
    advance_amount       DECIMAL(15, 2) NULL,
    advance_status       VARCHAR(12)   NULL,
    expenses_total       DECIMAL(15, 2) NOT NULL DEFAULT 0,
    reimbursement_status VARCHAR(12)   NULL,
    deleted              BIT(1)        NOT NULL DEFAULT 0,
    created_by           VARCHAR(64)   NULL,
    created_date         DATETIME(6)   NULL,
    updated_by           VARCHAR(64)   NULL,
    updated_date         DATETIME(6)   NULL,
    INDEX idx_employee_trip_company (company_id, start_date),
    INDEX idx_employee_trip_employee (employee_id)
);

-- changeset ERP-EMPLOYEE:20261120370000-2
-- Expense lines of a trip; the type comes from the company's expense-types list.
CREATE TABLE trip_expense
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT         NOT NULL,
    trip_id           BIGINT         NOT NULL,
    expense_type_id   BIGINT         NOT NULL,
    expense_date      DATE           NOT NULL,
    description       VARCHAR(500)   NULL,
    amount            DECIMAL(15, 2) NOT NULL,
    receipt_media_id  BIGINT         NULL,
    deleted           BIT(1)         NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)    NULL,
    created_date      DATETIME(6)    NULL,
    updated_by        VARCHAR(64)    NULL,
    updated_date      DATETIME(6)    NULL,
    INDEX idx_trip_expense_trip (trip_id),
    CONSTRAINT fk_trip_expense_trip FOREIGN KEY (trip_id) REFERENCES employee_trip (id),
    CONSTRAINT fk_trip_expense_type FOREIGN KEY (expense_type_id) REFERENCES lifecycle_catalog (id)
);
