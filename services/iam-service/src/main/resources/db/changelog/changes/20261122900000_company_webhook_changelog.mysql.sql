-- liquibase formatted sql

-- changeset ERP-IAM:20261122900000-1
-- Company webhooks (ERP manual 19; reference packages/workdo/Webhook webhooks: action, url, method, is_active).
-- event_code = EWebhookEvent; secret_enc = AES-GCM (SecretCipher) HMAC signing secret, null = unsigned.
CREATE TABLE company_webhook
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT        NOT NULL,
    event_code   VARCHAR(60)   NOT NULL,
    url          VARCHAR(1000) NOT NULL,
    method       VARCHAR(10)   NOT NULL DEFAULT 'POST',
    active       BIT(1)        NOT NULL DEFAULT 1,
    secret_enc   VARCHAR(1000) NULL,
    deleted      BIT(1)        NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_date DATETIME(6)   NULL,
    updated_by   VARCHAR(64)   NULL,
    updated_date DATETIME(6)   NULL,
    INDEX idx_company_webhook_company (company_id, deleted, event_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
