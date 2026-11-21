-- liquibase formatted sql

-- changeset ERP-IAM:20261120390000-1
-- Performance Management (docs/analysis/06 §7.3), checked by employee-service; must match PerformancePermissions
-- (PO, 2026-11-20). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('PERFORMANCE_VIEW', 'PERFORMANCE', 'View reviews, goals, review cycles, indicators, goal types and indicator categories'),
       ('PERFORMANCE_MANAGE', 'PERFORMANCE', 'Manage performance setup, goals and reviews, and conduct reviews')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261120390000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('PERFORMANCE_VIEW', 'PERFORMANCE_MANAGE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
