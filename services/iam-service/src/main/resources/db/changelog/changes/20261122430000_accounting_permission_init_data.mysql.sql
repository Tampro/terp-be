-- liquibase formatted sql

-- changeset ERP-IAM:20261122430000-1
-- Accounting (ERP manual 12) in accounting-service; must match SalesPermissions.ACCOUNTING. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('COA_VIEW', 'COA', 'View the chart of accounts, account types, categories, mapping and journal entries'),
       ('COA_MANAGE', 'COA', 'Manage the chart of accounts, account types, categories and the account mapping'),
       ('REVENUE_VIEW', 'REVENUE', 'View revenues'),
       ('REVENUE_MANAGE', 'REVENUE', 'Create, edit and delete draft revenues'),
       ('REVENUE_APPROVE', 'REVENUE', 'Approve and post revenues (bank + journal)'),
       ('EXPENSE_VIEW', 'EXPENSE', 'View expenses'),
       ('EXPENSE_MANAGE', 'EXPENSE', 'Create, edit and delete draft expenses'),
       ('EXPENSE_APPROVE', 'EXPENSE', 'Approve and post expenses (bank + journal)'),
       ('PAYMENT_VIEW', 'PAYMENT', 'View customer and vendor payments'),
       ('PAYMENT_MANAGE', 'PAYMENT', 'Create and delete pending payments'),
       ('PAYMENT_CLEAR', 'PAYMENT', 'Clear or cancel payments (invoice balances, bank, journal)'),
       ('NOTE_VIEW', 'NOTE', 'View credit and debit notes'),
       ('NOTE_MANAGE', 'NOTE', 'Create and delete draft credit and debit notes'),
       ('NOTE_APPROVE', 'NOTE', 'Approve credit and debit notes (journal)'),
       ('ACCOUNTING_REPORT_VIEW', 'ACCOUNTING_REPORT', 'View aging, tax, balance and party reports')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261122430000-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('COA_VIEW', 'COA_MANAGE', 'REVENUE_VIEW', 'REVENUE_MANAGE', 'REVENUE_APPROVE', 'EXPENSE_VIEW', 'EXPENSE_MANAGE', 'EXPENSE_APPROVE', 'PAYMENT_VIEW', 'PAYMENT_MANAGE', 'PAYMENT_CLEAR', 'NOTE_VIEW', 'NOTE_MANAGE', 'NOTE_APPROVE', 'ACCOUNTING_REPORT_VIEW')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
