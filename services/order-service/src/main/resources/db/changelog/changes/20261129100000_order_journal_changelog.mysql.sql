-- liquibase formatted sql

-- changeset ERP-ORDER:20261129100000-1
-- Journal of a paid order (order-invoice spec): requested through the outbox (SHOP_ORDER), answered by accounting.
ALTER TABLE customer_order
    ADD COLUMN journal_status   VARCHAR(16) NULL,
    ADD COLUMN journal_entry_no VARCHAR(40) NULL,
    ADD COLUMN journal_error    VARCHAR(40) NULL;

-- changeset ERP-ORDER:20261129100000-2
-- Purchase price of the item at the stock move: the cost of goods of the order's journal.
ALTER TABLE customer_order_line
    ADD COLUMN unit_cost DECIMAL(19, 4) NULL;

-- changeset ERP-ORDER:20261129100000-3
-- Transactional outbox (erp-common com.terp.erp.outbox.OutboxEvent), same shape as commerce-service's.
CREATE TABLE outbox_event
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id        VARCHAR(36)  NOT NULL,
    company_id      BIGINT       NULL,
    event_type      VARCHAR(100) NOT NULL,
    aggregate_type  VARCHAR(60)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    payload         TEXT         NOT NULL,
    trace_id        VARCHAR(64)  NULL,
    status          VARCHAR(10)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6)  NOT NULL,
    last_error      VARCHAR(100) NULL,
    created_at      DATETIME(6)  NOT NULL,
    sent_at         DATETIME(6)  NULL,
    UNIQUE KEY uk_outbox_event_event_id (event_id),
    INDEX idx_outbox_event_due (status, next_attempt_at),
    INDEX idx_outbox_event_aggregate (aggregate_type, aggregate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ORDER:20261129100000-4
-- One row per consumed accounting event (dedupe of at-least-once delivery).
CREATE TABLE processed_event
(
    event_id    VARCHAR(36)  NOT NULL PRIMARY KEY,
    company_id  BIGINT       NULL,
    event_type  VARCHAR(100) NOT NULL,
    received_at DATETIME(6)  NOT NULL,
    INDEX idx_processed_event_received (received_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
