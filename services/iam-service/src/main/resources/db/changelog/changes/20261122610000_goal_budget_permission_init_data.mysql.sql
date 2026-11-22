-- liquibase formatted sql

-- changeset ERP-IAM:20261122610000-1
-- Financial goals and budgets (ERP manual 14) in accounting-service; must match SalesPermissions.GOAL_BUDGET.
-- Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('GOAL_VIEW', 'GOAL', 'View financial goals, milestones, contributions and tracking'),
       ('GOAL_MANAGE', 'GOAL', 'Manage goal categories, goals, milestones and contributions'),
       ('BUDGET_VIEW', 'BUDGET', 'View budget periods, budgets, allocations and monitoring'),
       ('BUDGET_MANAGE', 'BUDGET', 'Create, edit and delete budget periods, budgets and allocations; close periods'),
       ('BUDGET_APPROVE', 'BUDGET', 'Approve, activate and close budgets')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261122610000-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('GOAL_VIEW', 'GOAL_MANAGE', 'BUDGET_VIEW', 'BUDGET_MANAGE', 'BUDGET_APPROVE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
