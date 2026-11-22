-- liquibase formatted sql

-- changeset ERP-IAM:20261122510000-1
-- Double entry (ERP manual 13) in accounting-service; must match SalesPermissions.LEDGER. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('LEDGER_VIEW', 'LEDGER', 'View journal entries'),
       ('LEDGER_MANAGE', 'LEDGER', 'Create, edit, delete and post manual journal entries; reverse posted entries'),
       ('LEDGER_REPORT_VIEW', 'LEDGER', 'View trial balance, P&L, cash flow, ledgers, statements, balance sheets'),
       ('LEDGER_CLOSE', 'LEDGER', 'Generate and finalise balance sheets; close the financial year (cannot be undone)')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261122510000-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('LEDGER_VIEW', 'LEDGER_MANAGE', 'LEDGER_REPORT_VIEW', 'LEDGER_CLOSE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
