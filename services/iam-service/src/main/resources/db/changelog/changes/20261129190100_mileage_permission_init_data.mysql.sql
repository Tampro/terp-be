-- liquibase formatted sql

-- changeset ERP-IAM:20261129190100-1
-- Mileage in order-service; must match MileagePermissions. Module ORDER (always on). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('MILEAGE_VIEW', 'ORDER', 'View the mileage policy and customer statements'),
       ('MILEAGE_MANAGE', 'ORDER', 'Change the mileage policy and adjust customer points')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261129190100-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('MILEAGE_VIEW', 'MILEAGE_MANAGE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
