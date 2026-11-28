-- liquibase formatted sql

-- changeset ERP-IAM:20261129200100-1
-- Back Office cancel + full refund of a paid Express order (order-service); must match OrderPermissions. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('ORDER_CANCEL', 'ORDER', 'Cancel a paid order before pick-up and refund it')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261129200100-2
-- Every COMPANY_ADMIN gets it (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code = 'ORDER_CANCEL'
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
