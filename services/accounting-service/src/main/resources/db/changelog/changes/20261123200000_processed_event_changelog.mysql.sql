-- liquibase formatted sql

-- changeset ERP-ACCOUNTING:20261123200000-1
-- One row per consumed event (commerce.journal.requested), written in the transaction of its effect (the journal
-- entry and accounting.journal.posted, or accounting.journal.failed): a redelivered message (RabbitMQ is
-- at-least-once) changes nothing. outcome = POSTED or FAILED:<error code>. No payload kept (amounts).
CREATE TABLE processed_event
(
    event_id    VARCHAR(36)  NOT NULL PRIMARY KEY,
    company_id  BIGINT       NULL,
    event_type  VARCHAR(100) NOT NULL,
    outcome     VARCHAR(40)  NULL,
    received_at DATETIME(6)  NOT NULL,
    INDEX idx_processed_event_received (received_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
