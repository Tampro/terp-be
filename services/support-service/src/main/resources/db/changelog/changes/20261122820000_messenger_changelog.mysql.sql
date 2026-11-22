-- liquibase formatted sql

-- changeset ERP-SUPPORT:20261122820000-1
-- One-to-one messages (reference Chatify ch_messages: from_id, to_id, body 5000, attachment, seen). Ids are IAM
-- user ids; company_id keeps every query inside one company. No soft delete (deleting messages is not in scope).
CREATE TABLE chat_message
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id         BIGINT        NOT NULL,
    from_user_id       BIGINT        NOT NULL,
    to_user_id         BIGINT        NOT NULL,
    body               VARCHAR(5000) NULL,
    attachment_file_id BIGINT        NULL,
    attachment_name    VARCHAR(255)  NULL,
    seen               BIT(1)        NOT NULL DEFAULT 0,
    seen_at            DATETIME(6)   NULL,
    created_by         VARCHAR(64)   NULL,
    updated_by         VARCHAR(64)   NULL,
    created_date       DATETIME(6)   NULL,
    updated_date       DATETIME(6)   NULL,
    INDEX idx_chat_message_pair (company_id, from_user_id, to_user_id, id),
    INDEX idx_chat_message_unread (company_id, to_user_id, seen)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-SUPPORT:20261122820000-2
-- Favorite contacts (reference ch_favorites: user_id, favorite_id).
CREATE TABLE chat_favorite
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    favorite_user_id BIGINT      NOT NULL,
    created_by       VARCHAR(64) NULL,
    updated_by       VARCHAR(64) NULL,
    created_date     DATETIME(6) NULL,
    updated_date     DATETIME(6) NULL,
    UNIQUE KEY uk_chat_favorite (company_id, user_id, favorite_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
