-- liquibase formatted sql

-- changeset ERP-IAM:20261122750000-1
-- Pay Salary (ERP manual 15, FR-ERP-PAY-004) in payroll-service; must match SalesPermissions.PAYROLL_PAYMENT.
-- Module PAYROLL so the HRM plan module governs it. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('PAYROLL_PAY', 'PAYROLL', 'Pay a completed payroll run (bank debit and journal entry in accounting)')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261122750000-2
-- Every COMPANY_ADMIN gets it (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('PAYROLL_PAY')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;

-- changeset ERP-IAM:20261122750000-3
-- Self service (ERP manual 15): clock in/out and read one's own profile. Modules ATTENDANCE / EMPLOYEE (HRM plan).
INSERT INTO permission (code, module, description)
VALUES ('ATTENDANCE_SELF', 'ATTENDANCE', 'Clock in and out for oneself'),
       ('EMPLOYEE_SELF', 'EMPLOYEE', 'Read one''s own employee profile')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261122750000-4
-- COMPANY_ADMIN, HR and EMPLOYEE roles of existing companies get them (EDefaultRole for new companies).
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('ATTENDANCE_SELF', 'EMPLOYEE_SELF')
WHERE r.code IN ('COMPANY_ADMIN', 'HR', 'EMPLOYEE')
  AND r.deleted = 0;
