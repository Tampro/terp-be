-- liquibase formatted sql

-- changeset ERP-SUPPORT:20261125500000-1
-- Messenger presence (core parity, §18): last heartbeat per user; online = seen within the last 2 minutes.
CREATE TABLE chat_presence
(
    company_id   BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    PRIMARY KEY (company_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
-- rollback DROP TABLE chat_presence;
