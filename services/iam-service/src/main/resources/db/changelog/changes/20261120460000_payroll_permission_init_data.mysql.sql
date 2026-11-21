-- liquibase formatted sql

-- changeset ERP-IAM:20261120460000-1
-- Payroll Management (docs/analysis/06 §8.1), checked by payroll-service; must match PayrollPermissions
-- (PO, 2026-11-20). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('PAYROLL_VIEW', 'PAYROLL', 'View salary components, employee salaries, payroll runs and payslips'),
       ('PAYROLL_MANAGE', 'PAYROLL', 'Manage salary components and salaries, process payroll runs and generate payslips')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261120460000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('PAYROLL_VIEW', 'PAYROLL_MANAGE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
