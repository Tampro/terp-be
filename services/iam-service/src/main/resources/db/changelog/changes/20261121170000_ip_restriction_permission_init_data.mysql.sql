-- liquibase formatted sql

-- changeset ERP-IAM:20261121170000-3
-- IAM_SETTINGS_MANAGE manages the company's IP restriction; part of IamPermissions.ALL, so every COMPANY_ADMIN role
-- holds it (IAM_012). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('IAM_SETTINGS_MANAGE', 'IAM', 'Manage the company settings of IAM (IP restriction)')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261121170000-4
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code = 'IAM_SETTINGS_MANAGE'
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
