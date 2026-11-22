-- liquibase formatted sql

-- changeset ERP-NOTIFICATION:20261123100000-1
-- One row per consumed broker event (key = the producer's eventId): a redelivered message finds it and creates no
-- second set of deliveries. envelope = the event as received (for retries); may hold recipient addresses.
CREATE TABLE processed_event
(
    event_id    VARCHAR(36)  NOT NULL PRIMARY KEY,
    company_id  BIGINT       NULL,
    event_type  VARCHAR(100) NOT NULL,
    envelope    TEXT         NOT NULL,
    received_at DATETIME(6)  NOT NULL,
    INDEX idx_processed_event_received (received_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-NOTIFICATION:20261123100000-2
-- One send of one event on one channel. target is masked (email *@domain, else host); last_error_code is a code,
-- never a body or an address.
CREATE TABLE delivery
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT       NOT NULL,
    event_id        VARCHAR(36)  NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    channel         VARCHAR(10)  NOT NULL,
    target          VARCHAR(255) NULL,
    recipient_ref   VARCHAR(40)  NULL,
    webhook_id      BIGINT       NULL,
    status          VARCHAR(10)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error_code VARCHAR(40)  NULL,
    next_attempt_at DATETIME(6)  NULL,
    sent_at         DATETIME(6)  NULL,
    created_by      VARCHAR(64)  NULL,
    updated_by      VARCHAR(64)  NULL,
    created_date    DATETIME(6)  NULL,
    updated_date    DATETIME(6)  NULL,
    INDEX idx_delivery_company (company_id, created_date),
    INDEX idx_delivery_event (event_id),
    INDEX idx_delivery_due (status, next_attempt_at),
    CONSTRAINT fk_delivery_event FOREIGN KEY (event_id) REFERENCES processed_event (event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
