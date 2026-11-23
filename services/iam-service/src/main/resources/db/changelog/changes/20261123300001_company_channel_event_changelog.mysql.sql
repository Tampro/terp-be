-- liquibase formatted sql

-- changeset ERP-IAM:20261123300001-1
-- Per-event switches of the chat channels (ERP manual 19): channel SLACK / TELEGRAM / SMS, event_type = the broker
-- routing key (EChannelEvent). No row for a company + channel = every event on.
CREATE TABLE company_channel_event
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    channel      VARCHAR(10)  NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    enabled      BIT(1)       NOT NULL DEFAULT 1,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    UNIQUE KEY uk_company_channel_event (company_id, channel, event_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
