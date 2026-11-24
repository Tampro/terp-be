-- liquibase formatted sql

-- changeset ERP-SUPPORT:20261125400000-1
-- Pinned conversations (core parity, §18): personal to the user who pins; pinned conversations list first.
CREATE TABLE chat_pin
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    peer_user_id BIGINT      NOT NULL,
    created_by   VARCHAR(64) NULL,
    updated_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_date DATETIME(6) NULL,
    UNIQUE KEY uk_chat_pin (company_id, user_id, peer_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
-- rollback DROP TABLE chat_pin;
