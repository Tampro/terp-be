-- liquibase formatted sql

-- changeset ERP-IAM:20261121360000-1
-- Purchase and vendors (ERP manual 8, 12.1), checked by commerce- and accounting-service; must match
-- SalesPermissions.PURCHASE / VENDOR. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('PURCHASE_VIEW', 'PURCHASE', 'View purchase invoices and purchase returns'),
       ('PURCHASE_MANAGE', 'PURCHASE', 'Create, edit and delete draft purchase invoices and returns'),
       ('PURCHASE_POST', 'PURCHASE', 'Post purchase invoices; approve and complete purchase returns (moves stock)'),
       ('VENDOR_VIEW', 'VENDOR', 'View vendors'),
       ('VENDOR_MANAGE', 'VENDOR', 'Create, edit and delete vendors')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261121360000-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('PURCHASE_VIEW', 'PURCHASE_MANAGE', 'PURCHASE_POST', 'VENDOR_VIEW',
                                         'VENDOR_MANAGE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
