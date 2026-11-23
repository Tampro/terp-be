-- liquibase formatted sql

-- changeset ERP-PROJECT:20261123100000-1
-- Transactional outbox (erp-common com.terp.erp.outbox.OutboxEvent): an event is written in the transaction of
-- the change it describes and published to RabbitMQ (exchange erp.events) by the relay. status NEW / SENT / FAILED.
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
