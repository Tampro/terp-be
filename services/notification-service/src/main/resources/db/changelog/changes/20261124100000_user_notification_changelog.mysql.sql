-- liquibase formatted sql

-- changeset ERP-NOTIFICATION:20261124100000-1
-- In-app feed: one row per (event, recipient IAM user). title is a short line built from display fields of the
-- payload (never a chat body or an email body). link_type / link_id point the FE at the document.
CREATE TABLE user_notification
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    event_id   VARCHAR(36)  NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    link_type  VARCHAR(50)  NULL,
    link_id    VARCHAR(64)  NULL,
    read_at    DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT uk_user_notification_event_user UNIQUE (event_id, user_id),
    INDEX idx_user_notification_owner (company_id, user_id, id),
    INDEX idx_user_notification_unread (company_id, user_id, read_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
