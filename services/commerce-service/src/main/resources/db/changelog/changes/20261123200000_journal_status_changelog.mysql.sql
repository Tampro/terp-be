-- liquibase formatted sql

-- changeset ERP-COMMERCE:20261123200000-1
-- Accounting journal of a posted invoice / completed return, now written asynchronously by accounting-service from
-- the outbox event commerce.journal.requested: PENDING → POSTED | FAILED (journal_error_code = accounting's code,
-- e.g. ACC_070). NULL = draft / nothing to journal. Documents posted before this change were journalled
-- synchronously (PR #20), so they start as POSTED.
ALTER TABLE sales_document
    ADD COLUMN journal_status     VARCHAR(10) NULL,
    ADD COLUMN journal_error_code VARCHAR(30) NULL,
    ADD INDEX idx_sales_document_journal (company_id, journal_status);
UPDATE sales_document
SET journal_status = 'POSTED'
WHERE kind = 'INVOICE' AND status <> 'DRAFT';

ALTER TABLE purchase_invoice
    ADD COLUMN journal_status     VARCHAR(10) NULL,
    ADD COLUMN journal_error_code VARCHAR(30) NULL,
    ADD INDEX idx_purchase_invoice_journal (company_id, journal_status);
UPDATE purchase_invoice
SET journal_status = 'POSTED'
WHERE status <> 'DRAFT';

ALTER TABLE sales_return
    ADD COLUMN journal_status     VARCHAR(10) NULL,
    ADD COLUMN journal_error_code VARCHAR(30) NULL;
UPDATE sales_return
SET journal_status = 'POSTED'
WHERE status = 'COMPLETED';

ALTER TABLE purchase_return
    ADD COLUMN journal_status     VARCHAR(10) NULL,
    ADD COLUMN journal_error_code VARCHAR(30) NULL;
UPDATE purchase_return
SET journal_status = 'POSTED'
WHERE status = 'COMPLETED';

-- changeset ERP-COMMERCE:20261123200000-2
-- One row per consumed accounting event (journal results, cleared payments), written in the transaction of its
-- effect: a redelivered message (RabbitMQ is at-least-once) changes nothing. No payload kept (amounts).
CREATE TABLE processed_event
(
    event_id    VARCHAR(36)  NOT NULL PRIMARY KEY,
    company_id  BIGINT       NULL,
    event_type  VARCHAR(100) NOT NULL,
    received_at DATETIME(6)  NOT NULL,
    INDEX idx_processed_event_received (received_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
