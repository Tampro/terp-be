-- liquibase formatted sql

-- changeset ERP-IAM:20261127200100-1
-- Stores in store-service; must match StorePermissions. Module STORE is always on (EPlanModule.ALWAYS_ON). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('STORE_VIEW', 'STORE', 'View stores: warehouse, order hours, delivery fees'),
       ('STORE_MANAGE', 'STORE', 'Create, edit and delete stores')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261127200100-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('STORE_VIEW', 'STORE_MANAGE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
