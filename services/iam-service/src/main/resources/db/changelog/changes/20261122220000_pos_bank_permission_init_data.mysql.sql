-- liquibase formatted sql

-- changeset ERP-IAM:20261122220000-1
-- POS (ERP manual 10) in commerce-service and bank accounts in accounting-service; must match
-- SalesPermissions.POS / BANK. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('POS_VIEW', 'POS', 'View POS orders and receipts'),
       ('POS_SELL', 'POS', 'Sell at the POS (moves stock, records income) and print barcodes'),
       ('POS_REPORT', 'POS', 'View POS sales, product and customer reports'),
       ('BANK_VIEW', 'BANK', 'View bank accounts and their transactions'),
       ('BANK_MANAGE', 'BANK', 'Create, edit and delete bank accounts')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261122220000-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('POS_VIEW', 'POS_SELL', 'POS_REPORT', 'BANK_VIEW', 'BANK_MANAGE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
