-- liquibase formatted sql

-- changeset ERP-IAM:20261120200000-1
-- Permissions checked by attendance-service; must match AttendancePermissions / EDefaultRole. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('ATTENDANCE_VIEW', 'ATTENDANCE', 'View shifts, attendance policies and attendance data'),
       ('ATTENDANCE_MANAGE', 'ATTENDANCE', 'Manage shifts, attendance policies and attendance data')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261120200000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('ATTENDANCE_VIEW', 'ATTENDANCE_MANAGE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
