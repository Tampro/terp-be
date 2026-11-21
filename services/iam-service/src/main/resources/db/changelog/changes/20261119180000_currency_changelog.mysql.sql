-- liquibase formatted sql

-- changeset ERP-IAM:20261119180000-1
CREATE TABLE currency
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(10)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    symbol       VARCHAR(10)  NULL,
    description  VARCHAR(500) NULL,
    is_default   BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    CONSTRAINT uk_currency_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
