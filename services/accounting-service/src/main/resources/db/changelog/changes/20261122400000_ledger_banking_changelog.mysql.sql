-- liquibase formatted sql

-- changeset ERP-ACCOUNTING:20261122400000-1
-- Account types of a company's chart (seeded per company on first use, VAS TT200). code unique per company.
CREATE TABLE account_type
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT       NOT NULL,
    category       VARCHAR(20)  NOT NULL,
    code           VARCHAR(20)  NOT NULL,
    name           VARCHAR(255) NOT NULL,
    normal_balance VARCHAR(10)  NOT NULL,
    active         BIT(1)       NOT NULL DEFAULT 1,
    created_by     VARCHAR(64)  NULL,
    created_date   DATETIME(6)  NULL,
    updated_by     VARCHAR(64)  NULL,
    updated_date   DATETIME(6)  NULL,
    UNIQUE KEY uk_account_type_code (company_id, code)
);

-- changeset ERP-ACCOUNTING:20261122400000-2
-- Ledger accounts. current_balance = opening_balance + posted journal lines on the normal side (kept by the
-- journal under a row lock). Deleting is refused while lines, children or references exist (hard delete).
CREATE TABLE chart_account
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT         NOT NULL,
    account_type_id BIGINT         NOT NULL,
    code            VARCHAR(20)    NOT NULL,
    name            VARCHAR(255)   NOT NULL,
    normal_balance  VARCHAR(10)    NOT NULL,
    opening_balance DECIMAL(19, 4) NOT NULL DEFAULT 0,
    current_balance DECIMAL(19, 4) NOT NULL DEFAULT 0,
    active          BIT(1)         NOT NULL DEFAULT 1,
    parent_id       BIGINT         NULL,
    description     VARCHAR(500)   NULL,
    created_by      VARCHAR(64)    NULL,
    created_date    DATETIME(6)    NULL,
    updated_by      VARCHAR(64)    NULL,
    updated_date    DATETIME(6)    NULL,
    UNIQUE KEY uk_chart_account_code (company_id, code),
    INDEX idx_chart_account_type (account_type_id),
    INDEX idx_chart_account_parent (parent_id),
    CONSTRAINT fk_chart_account_type FOREIGN KEY (account_type_id) REFERENCES account_type (id),
    CONSTRAINT fk_chart_account_parent FOREIGN KEY (parent_id) REFERENCES chart_account (id)
);

-- changeset ERP-ACCOUNTING:20261122400000-3
-- Revenue (kind REVENUE) and expense (kind EXPENSE) categories; code unique per company and kind.
CREATE TABLE account_category
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    kind          VARCHAR(10)  NOT NULL,
    code          VARCHAR(30)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    gl_account_id BIGINT       NOT NULL,
    description   VARCHAR(500) NULL,
    enabled       BIT(1)       NOT NULL DEFAULT 1,
    created_by    VARCHAR(64)  NULL,
    created_date  DATETIME(6)  NULL,
    updated_by    VARCHAR(64)  NULL,
    updated_date  DATETIME(6)  NULL,
    UNIQUE KEY uk_account_category_code (company_id, kind, code),
    CONSTRAINT fk_account_category_gl FOREIGN KEY (gl_account_id) REFERENCES chart_account (id)
);

-- changeset ERP-ACCOUNTING:20261122400000-4
-- System account mapping (BANK, SALES_REVENUE, ...) per company, plus the per-company seed marker.
CREATE TABLE account_mapping
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT      NOT NULL,
    system_key VARCHAR(30) NOT NULL,
    account_id BIGINT      NOT NULL,
    UNIQUE KEY uk_account_mapping_key (company_id, system_key),
    CONSTRAINT fk_account_mapping_account FOREIGN KEY (account_id) REFERENCES chart_account (id)
);

CREATE TABLE ledger_setup
(
    company_id BIGINT PRIMARY KEY,
    seeded     BIT(1) NOT NULL DEFAULT 0
);

-- changeset ERP-ACCOUNTING:20261122400000-5
-- Document numbers per company and key (JE-2026, REV-2026-11, EXP-2026-11, TRF-2026-11 ...).
CREATE TABLE accounting_sequence
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id  BIGINT      NOT NULL,
    seq_key     VARCHAR(30) NOT NULL,
    last_number BIGINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_accounting_sequence (company_id, seq_key)
);

-- changeset ERP-ACCOUNTING:20261122400000-6
-- Journal. (company_id, source_type, source_id) unique so a posting is journalled once; manual entries (§13)
-- have no source (NULLs do not collide). entry_number unique per company.
CREATE TABLE journal_entry
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    entry_number VARCHAR(30)    NOT NULL,
    entry_date   DATE           NOT NULL,
    entry_type   VARCHAR(20)    NOT NULL,
    reference    VARCHAR(100)   NULL,
    description  VARCHAR(500)   NULL,
    source_type  VARCHAR(30)    NULL,
    source_id    BIGINT         NULL,
    status       VARCHAR(20)    NOT NULL,
    total_debit  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    total_credit DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_by   VARCHAR(64)    NULL,
    updated_date DATETIME(6)    NULL,
    UNIQUE KEY uk_journal_entry_number (company_id, entry_number),
    UNIQUE KEY uk_journal_entry_source (company_id, source_type, source_id),
    INDEX idx_journal_entry_date (company_id, entry_date)
);

CREATE TABLE journal_line
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    entry_id    BIGINT         NOT NULL,
    account_id  BIGINT         NOT NULL,
    description VARCHAR(500)   NULL,
    debit       DECIMAL(19, 4) NOT NULL DEFAULT 0,
    credit      DECIMAL(19, 4) NOT NULL DEFAULT 0,
    INDEX idx_journal_line_account (account_id),
    CONSTRAINT fk_journal_line_entry FOREIGN KEY (entry_id) REFERENCES journal_entry (id),
    CONSTRAINT fk_journal_line_account FOREIGN KEY (account_id) REFERENCES chart_account (id)
);

-- changeset ERP-ACCOUNTING:20261122400000-7
-- Bank account details (manual §12.2). gl_account_id NULL on older rows = the company's BANK mapping.
ALTER TABLE bank_account
    ADD COLUMN branch          VARCHAR(255) NULL,
    ADD COLUMN account_type    VARCHAR(20)  NOT NULL DEFAULT 'CHECKING',
    ADD COLUMN gl_account_id   BIGINT       NULL,
    ADD COLUMN payment_gateway VARCHAR(100) NULL,
    ADD COLUMN iban            VARCHAR(34)  NULL,
    ADD COLUMN swift           VARCHAR(11)  NULL,
    ADD COLUMN routing_number  VARCHAR(30)  NULL;

-- changeset ERP-ACCOUNTING:20261122400000-8
-- Bank transactions become CREDIT/DEBIT with reference, running balance and a reconciliation status. Old INCOME
-- rows (POS) are money in = CREDIT, already cleared; their running balance is unknown (NULL).
ALTER TABLE bank_transaction
    ADD COLUMN reference       VARCHAR(100)   NULL,
    ADD COLUMN running_balance DECIMAL(19, 4) NULL,
    ADD COLUMN status          VARCHAR(20)    NOT NULL DEFAULT 'CLEARED';
UPDATE bank_transaction SET type = 'CREDIT' WHERE type = 'INCOME';

-- changeset ERP-ACCOUNTING:20261122400000-9
-- Bank transfers between two accounts of a company: PENDING until processed into two bank transactions.
CREATE TABLE bank_transfer
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT         NOT NULL,
    transfer_number VARCHAR(30)    NULL,
    transfer_date   DATE           NOT NULL,
    from_account_id BIGINT         NOT NULL,
    to_account_id   BIGINT         NOT NULL,
    amount          DECIMAL(19, 4) NOT NULL,
    charges         DECIMAL(19, 4) NOT NULL DEFAULT 0,
    reference       VARCHAR(100)   NULL,
    description     VARCHAR(500)   NULL,
    status          VARCHAR(20)    NOT NULL,
    deleted         BIT(1)         NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)    NULL,
    created_date    DATETIME(6)    NULL,
    updated_by      VARCHAR(64)    NULL,
    updated_date    DATETIME(6)    NULL,
    INDEX idx_bank_transfer_company (company_id, transfer_date),
    CONSTRAINT fk_bank_transfer_from FOREIGN KEY (from_account_id) REFERENCES bank_account (id),
    CONSTRAINT fk_bank_transfer_to FOREIGN KEY (to_account_id) REFERENCES bank_account (id)
);

-- changeset ERP-ACCOUNTING:20261122400000-10
-- Revenues (kind REVENUE, REV-YYYY-MM-NNN) and expenses (kind EXPENSE, EXP-YYYY-MM-NNN): DRAFT -> APPROVED ->
-- POSTED. voucher_number unique per company.
CREATE TABLE cash_voucher
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT         NOT NULL,
    kind            VARCHAR(10)    NOT NULL,
    voucher_number  VARCHAR(30)    NOT NULL,
    voucher_date    DATE           NOT NULL,
    category_id     BIGINT         NOT NULL,
    bank_account_id BIGINT         NOT NULL,
    account_id      BIGINT         NOT NULL,
    amount          DECIMAL(19, 4) NOT NULL,
    reference       VARCHAR(100)   NULL,
    description     VARCHAR(500)   NULL,
    status          VARCHAR(20)    NOT NULL,
    approved_by     BIGINT         NULL,
    approved_at     DATETIME(6)    NULL,
    deleted         BIT(1)         NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)    NULL,
    created_date    DATETIME(6)    NULL,
    updated_by      VARCHAR(64)    NULL,
    updated_date    DATETIME(6)    NULL,
    UNIQUE KEY uk_cash_voucher_number (company_id, voucher_number),
    INDEX idx_cash_voucher_list (company_id, kind, voucher_date),
    CONSTRAINT fk_cash_voucher_category FOREIGN KEY (category_id) REFERENCES account_category (id),
    CONSTRAINT fk_cash_voucher_bank FOREIGN KEY (bank_account_id) REFERENCES bank_account (id),
    CONSTRAINT fk_cash_voucher_account FOREIGN KEY (account_id) REFERENCES chart_account (id)
);
