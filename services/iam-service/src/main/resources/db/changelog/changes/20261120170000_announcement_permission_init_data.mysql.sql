-- liquibase formatted sql

-- changeset ERP-IAM:20261120170000-1
-- Announcements (docs/analysis/06 �6.3.5), checked by employee-service; must match EmployeePermissions. Idempotent.
-- Reading one's own announcement feed needs no permission, only an employee profile.
INSERT INTO permission (code, module, description)
VALUES ('ANNOUNCEMENT_VIEW', 'ANNOUNCEMENT', 'View announcements and their engagement statistics'),
       ('ANNOUNCEMENT_MANAGE', 'ANNOUNCEMENT', 'Add, edit and delete announcements and their categories')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261120170000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('ANNOUNCEMENT_VIEW', 'ANNOUNCEMENT_MANAGE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
