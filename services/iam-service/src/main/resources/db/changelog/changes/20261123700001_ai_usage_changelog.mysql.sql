-- liquibase formatted sql

-- changeset ERP-IAM:20261123700001-1
-- One row per AI compose attempt that passed the pre-checks. No text is stored: only sizes, so the log shows usage
-- without keeping what users typed or what the provider wrote.
CREATE TABLE ai_usage
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT      NOT NULL,
    user_id      BIGINT      NULL,
    purpose      VARCHAR(30) NOT NULL,
    provider     VARCHAR(20) NOT NULL,
    model        VARCHAR(100) NOT NULL,
    status       VARCHAR(30) NOT NULL,
    input_chars  INT         NOT NULL,
    output_chars INT         NOT NULL DEFAULT 0,
    latency_ms   BIGINT      NULL,
    created_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL,
    INDEX idx_ai_usage_company (company_id, created_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
