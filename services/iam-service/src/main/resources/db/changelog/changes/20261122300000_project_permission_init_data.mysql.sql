-- liquibase formatted sql

-- changeset ERP-IAM:20261122300000-1
-- Projects and form builder (ERP manual 11.1, 11.2) in project-service; must match SalesPermissions.PROJECT / FORM.
-- Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('PROJECT_VIEW', 'PROJECT', 'View projects, milestones, tasks, bugs and the project report'),
       ('PROJECT_MANAGE', 'PROJECT', 'Create, edit, duplicate and delete projects; manage team, clients, milestones, stages'),
       ('PROJECT_TASK_MANAGE', 'PROJECT', 'Create, edit, move and delete tasks and bugs; comment'),
       ('FORM_VIEW', 'FORM', 'View forms and their responses'),
       ('FORM_MANAGE', 'FORM', 'Create, edit and delete forms; convert responses')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261122300000-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('PROJECT_VIEW', 'PROJECT_MANAGE', 'PROJECT_TASK_MANAGE', 'FORM_VIEW', 'FORM_MANAGE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
