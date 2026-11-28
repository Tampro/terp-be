-- liquibase formatted sql

-- changeset ERP-IAM:20261130100100-1
-- Customer returns in order-service; must match ReturnPermissions. Module ORDER (always on). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('ORDER_RETURN_VIEW', 'ORDER', 'View customer returns and the return policy'),
       ('ORDER_RETURN_MANAGE', 'ORDER', 'Approve, reject and complete returns; change the return policy')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261130100100-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('ORDER_RETURN_VIEW', 'ORDER_RETURN_MANAGE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
