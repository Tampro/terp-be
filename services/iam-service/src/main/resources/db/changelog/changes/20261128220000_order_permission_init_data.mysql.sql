-- liquibase formatted sql

-- changeset ERP-IAM:20261128220000-1
-- Customer orders in order-service (Back Office view); must match OrderPermissions. Module ORDER is always on
-- (EPlanModule.ALWAYS_ON). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('ORDER_VIEW', 'ORDER', 'View customer orders of the company''s stores')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261128220000-2
-- Every COMPANY_ADMIN gets it (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code = 'ORDER_VIEW'
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
