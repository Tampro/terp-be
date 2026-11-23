-- liquibase formatted sql

-- changeset ERP-IAM:20261123600000-1
-- Super admin webhooks for platform events (ERP manual 3.7 "Webhooks: method, event, URL"). No company_id: these
-- receive the platform.* outbox events (companyId null). event_code = EPlatformWebhookEvent; secret_enc = AES-GCM
-- (SecretCipher) HMAC signing secret, null = unsigned. method: POST only (see CompanyWebhook.EMethod).
CREATE TABLE platform_webhook
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    INDEX idx_platform_webhook_event (deleted, event_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
