-- liquibase formatted sql

-- changeset ERP-ACCOUNTING:20261122700000-1
-- Commerce and payroll postings need 5213 (sales returns) and 6421 (administrative staff costs), new in the default
-- chart. Companies seeded before this change get them under their 521 / 642, plus the SERVICE_REVENUE (5113),
-- SALES_RETURNS (5213) and SALARY_EXPENSE (6421) mapping rows; INSERT IGNORE skips companies that already have the
-- code or the key. Companies without 521 / 642 / 5113 map the keys themselves.
-- TODO(assumption): accounts to be confirmed by the chief accountant.
INSERT IGNORE INTO chart_account (company_id, account_type_id, code, name, normal_balance, opening_balance,
                                  current_balance, active, parent_id, description, created_by, created_date)
SELECT p.company_id, p.account_type_id, '5213', 'Hàng bán bị trả lại', 'DEBIT', 0, 0, 1,
       p.id, 'Sales returns', 'liquibase', UTC_TIMESTAMP(6)
FROM chart_account p
WHERE p.code = '521';

INSERT IGNORE INTO chart_account (company_id, account_type_id, code, name, normal_balance, opening_balance,
                                  current_balance, active, parent_id, description, created_by, created_date)
SELECT p.company_id, p.account_type_id, '6421', 'Chi phí nhân viên quản lý', 'DEBIT', 0, 0, 1,
       p.id, 'Administrative staff costs', 'liquibase', UTC_TIMESTAMP(6)
FROM chart_account p
WHERE p.code = '642';

INSERT IGNORE INTO account_mapping (company_id, system_key, account_id)
SELECT a.company_id, 'SERVICE_REVENUE', a.id
FROM chart_account a
         JOIN ledger_setup s ON s.company_id = a.company_id AND s.seeded = 1
WHERE a.code = '5113';

INSERT IGNORE INTO account_mapping (company_id, system_key, account_id)
SELECT a.company_id, 'SALES_RETURNS', a.id
FROM chart_account a
         JOIN ledger_setup s ON s.company_id = a.company_id AND s.seeded = 1
WHERE a.code = '5213';

INSERT IGNORE INTO account_mapping (company_id, system_key, account_id)
SELECT a.company_id, 'SALARY_EXPENSE', a.id
FROM chart_account a
         JOIN ledger_setup s ON s.company_id = a.company_id AND s.seeded = 1
WHERE a.code = '6421';
