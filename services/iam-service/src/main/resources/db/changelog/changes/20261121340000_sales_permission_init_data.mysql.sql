-- liquibase formatted sql

-- changeset ERP-IAM:20261121340000-1
-- Commercial modules (ERP manual 6, 8, 9, 12.1), checked by inventory-, commerce- and accounting-service; must match
-- SalesPermissions. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('INVENTORY_VIEW', 'INVENTORY', 'View items, categories, taxes, units, warehouses and stock'),
       ('INVENTORY_MANAGE', 'INVENTORY', 'Manage items, categories, taxes, units and warehouses; add stock'),
       ('CUSTOMER_VIEW', 'CUSTOMER', 'View customers'),
       ('CUSTOMER_MANAGE', 'CUSTOMER', 'Create, edit and delete customers'),
       ('SALES_VIEW', 'SALES', 'View proposals, sales invoices and sales returns'),
       ('SALES_MANAGE', 'SALES', 'Create, edit, send and convert proposals; create invoices and returns'),
       ('SALES_POST', 'SALES', 'Post sales invoices; approve and complete sales returns (moves stock)'),
       ('QUOTATION_VIEW', 'QUOTATION', 'View quotations'),
       ('QUOTATION_MANAGE', 'QUOTATION', 'Create, edit, send, version and convert quotations')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261121340000-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('INVENTORY_VIEW', 'INVENTORY_MANAGE', 'CUSTOMER_VIEW', 'CUSTOMER_MANAGE',
                                         'SALES_VIEW', 'SALES_MANAGE', 'SALES_POST', 'QUOTATION_VIEW',
                                         'QUOTATION_MANAGE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
