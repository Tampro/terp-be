-- liquibase formatted sql

-- changeset ERP-IAM:20261120420000-1
-- Training & Development (docs/analysis/06 §7.4), checked by employee-service; must match TrainingPermissions
-- (PO, 2026-11-20). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('TRAINING_VIEW', 'TRAINING', 'View training types, programs, sessions, employee trainings and assessments'),
       ('TRAINING_MANAGE', 'TRAINING', 'Manage training setup, sessions and attendance, assign trainings and record results')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261120420000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('TRAINING_VIEW', 'TRAINING_MANAGE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
