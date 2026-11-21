-- liquibase formatted sql

-- changeset ERP-ACCOUNTING:20261122200000-1
-- Bank accounts of a company. balance = opening_balance + sum of transactions, kept by the service under a row
-- lock. account_number unique per company among not-deleted rows (checked by the service: a soft-deleted row
-- keeps its value, so a DB unique key would block re-adding it).
CREATE TABLE bank_account
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT         NOT NULL,
    holder_name     VARCHAR(255)   NOT NULL,
    bank_name       VARCHAR(255)   NOT NULL,
    account_number  VARCHAR(50)    NOT NULL,
    opening_balance DECIMAL(19, 4) NOT NULL DEFAULT 0,
    balance         DECIMAL(19, 4) NOT NULL DEFAULT 0,
    active          BIT(1)         NOT NULL DEFAULT 1,
    deleted         BIT(1)         NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)    NULL,
    created_date    DATETIME(6)    NULL,
    updated_by      VARCHAR(64)    NULL,
    updated_date    DATETIME(6)    NULL,
    INDEX idx_bank_account_company (company_id),
    INDEX idx_bank_account_number (company_id, account_number)
);

-- changeset ERP-ACCOUNTING:20261122200000-2
-- Money movements on a bank account. (company_id, source_type, source_id) is unique so a caller (e.g. a POS
-- sale) retrying the same income never books it twice.
CREATE TABLE bank_transaction
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT         NOT NULL,
    bank_account_id  BIGINT         NOT NULL,
    type             VARCHAR(20)    NOT NULL,
    amount           DECIMAL(19, 4) NOT NULL,
    transaction_date DATE           NOT NULL,
    source_type      VARCHAR(30)    NOT NULL,
    source_id        BIGINT         NOT NULL,
    description      VARCHAR(500)   NULL,
    created_by       VARCHAR(64)    NULL,
    created_date     DATETIME(6)    NULL,
    updated_by       VARCHAR(64)    NULL,
    updated_date     DATETIME(6)    NULL,
    UNIQUE KEY uk_bank_transaction_source (company_id, source_type, source_id),
    INDEX idx_bank_transaction_account (bank_account_id, transaction_date),
    CONSTRAINT fk_bank_transaction_account FOREIGN KEY (bank_account_id) REFERENCES bank_account (id)
);
