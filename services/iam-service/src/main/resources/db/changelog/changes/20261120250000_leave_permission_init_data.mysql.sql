-- liquibase formatted sql

-- changeset ERP-IAM:20261120250000-1
-- Leave Management (docs/analysis/06 §6.5), checked by attendance-service; must match AttendancePermissions. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('LEAVE_VIEW', 'LEAVE', 'View leave types, policies, applications and balances'),
       ('LEAVE_MANAGE', 'LEAVE', 'Manage leave types and policies, decide applications, sync balances')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261120250000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('LEAVE_VIEW', 'LEAVE_MANAGE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
