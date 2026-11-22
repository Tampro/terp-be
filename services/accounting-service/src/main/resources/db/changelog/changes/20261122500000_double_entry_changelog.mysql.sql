-- liquibase formatted sql

-- changeset ERP-ACCOUNTING:20261122500000-1
-- Manual journal (manual 13): reference type, and the two-way link between an entry and its reversal.
ALTER TABLE journal_entry
    ADD COLUMN reference_type VARCHAR(30) NULL,
    ADD COLUMN reversal_of_id BIGINT      NULL,
    ADD COLUMN reversed_by_id BIGINT      NULL,
    ADD INDEX idx_journal_entry_status (company_id, entry_type, status, entry_date);

-- changeset ERP-ACCOUNTING:20261122500000-2
-- Closed financial years: once per company and year, never deleted. Entries dated on or before the end of the
-- latest closed year are refused.
CREATE TABLE fiscal_year_close
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT      NOT NULL,
    fiscal_year  INT         NOT NULL,
    closing_date DATE        NOT NULL,
    entry_id     BIGINT      NULL,
    closed_by    VARCHAR(64) NULL,
    closed_at    DATETIME(6) NULL,
    UNIQUE KEY uk_fiscal_year_close (company_id, fiscal_year),
    CONSTRAINT fk_fiscal_year_close_entry FOREIGN KEY (entry_id) REFERENCES journal_entry (id)
);

-- changeset ERP-ACCOUNTING:20261122500000-3
-- Balance sheet snapshots: DRAFT (regenerate, notes, delete) -> FINALISED (locked). Lines copy account code and
-- name so a finalised sheet does not change when the chart does.
CREATE TABLE balance_sheet
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT         NOT NULL,
    sheet_date        DATE           NOT NULL,
    financial_year    INT            NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    notes             VARCHAR(2000)  NULL,
    total_assets      DECIMAL(19, 4) NOT NULL DEFAULT 0,
    total_liabilities DECIMAL(19, 4) NOT NULL DEFAULT 0,
    total_equity      DECIMAL(19, 4) NOT NULL DEFAULT 0,
    difference        DECIMAL(19, 4) NOT NULL DEFAULT 0,
    finalised_at      DATETIME(6)    NULL,
    created_by        VARCHAR(64)    NULL,
    created_date      DATETIME(6)    NULL,
    updated_by        VARCHAR(64)    NULL,
    updated_date      DATETIME(6)    NULL,
    INDEX idx_balance_sheet_company (company_id, financial_year, sheet_date)
);

CREATE TABLE balance_sheet_line
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    sheet_id     BIGINT         NOT NULL,
    section      VARCHAR(20)    NOT NULL,
    group_code   VARCHAR(30)    NOT NULL,
    account_id   BIGINT         NULL,
    account_code VARCHAR(20)    NULL,
    account_name VARCHAR(255)   NOT NULL,
    amount       DECIMAL(19, 4) NOT NULL DEFAULT 0,
    sort_order   INT            NOT NULL DEFAULT 0,
    CONSTRAINT fk_balance_sheet_line_sheet FOREIGN KEY (sheet_id) REFERENCES balance_sheet (id)
);

-- changeset ERP-ACCOUNTING:20261122500000-4
-- Year-end close needs 911 (already in the default chart) and 4212 (new). Companies seeded before this change get
-- 4211 / 4212 under their 421 and the INCOME_SUMMARY / RETAINED_EARNINGS mapping rows; INSERT IGNORE skips
-- companies that already have the code or the key. Companies without 911 / 421 map the keys themselves.
INSERT IGNORE INTO chart_account (company_id, account_type_id, code, name, normal_balance, opening_balance,
                                  current_balance, active, parent_id, description, created_by, created_date)
SELECT p.company_id, p.account_type_id, '4211', 'Lợi nhuận sau thuế chưa phân phối năm trước', 'CREDIT', 0, 0, 1,
       p.id, 'Retained earnings, prior years', 'liquibase', UTC_TIMESTAMP(6)
FROM chart_account p
WHERE p.code = '421';

INSERT IGNORE INTO chart_account (company_id, account_type_id, code, name, normal_balance, opening_balance,
                                  current_balance, active, parent_id, description, created_by, created_date)
SELECT p.company_id, p.account_type_id, '4212', 'Lợi nhuận sau thuế chưa phân phối năm nay', 'CREDIT', 0, 0, 1,
       p.id, 'Retained earnings, current year', 'liquibase', UTC_TIMESTAMP(6)
FROM chart_account p
WHERE p.code = '421';

INSERT IGNORE INTO account_mapping (company_id, system_key, account_id)
SELECT a.company_id, 'INCOME_SUMMARY', a.id
FROM chart_account a
         JOIN ledger_setup s ON s.company_id = a.company_id AND s.seeded = 1
WHERE a.code = '911';

INSERT IGNORE INTO account_mapping (company_id, system_key, account_id)
SELECT a.company_id, 'RETAINED_EARNINGS', a.id
FROM chart_account a
         JOIN ledger_setup s ON s.company_id = a.company_id AND s.seeded = 1
WHERE a.code = '4212';
