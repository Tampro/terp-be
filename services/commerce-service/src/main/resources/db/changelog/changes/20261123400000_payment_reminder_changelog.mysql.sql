-- liquibase formatted sql

-- changeset ERP-COMMERCE:20261123400000-1
-- Customer email copied from accounting-service when a sales document is saved: the daily payment reminder runs
-- without a user token. Existing rows stay NULL (no reminder email until the document is saved again).
ALTER TABLE sales_document ADD COLUMN customer_email VARCHAR(255) NULL AFTER customer_name;

-- changeset ERP-COMMERCE:20261123400000-2
-- One payment reminder per invoice per day (PaymentReminderService), written with its outbox event.
CREATE TABLE payment_reminder_log
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT      NOT NULL,
    invoice_id    BIGINT      NOT NULL,
    reminder_date DATE        NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    UNIQUE KEY uk_payment_reminder_log_invoice_day (invoice_id, reminder_date),
    INDEX idx_payment_reminder_log_company (company_id, reminder_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-COMMERCE:20261123400000-3
-- The reminder scan: open invoices past their due date.
ALTER TABLE sales_document ADD INDEX idx_sales_document_overdue (kind, status, due_date);
