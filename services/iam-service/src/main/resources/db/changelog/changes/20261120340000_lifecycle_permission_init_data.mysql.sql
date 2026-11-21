-- liquibase formatted sql

-- changeset ERP-IAM:20261120340000-1
-- Employee Lifecycle (docs/analysis/06 §7.2), checked by employee-service; must match LifecyclePermissions.
-- MANAGE also approves promotions / transfers and completes resignations / terminations, which change the employee
-- record (PO, 2026-11-20). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('LIFECYCLE_VIEW', 'LIFECYCLE', 'View awards, promotions, transfers, warnings, resignations, terminations, trips and complaints'),
       ('LIFECYCLE_MANAGE', 'LIFECYCLE', 'Manage and decide employee lifecycle events; approvals update the employee record')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261120340000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('LIFECYCLE_VIEW', 'LIFECYCLE_MANAGE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
