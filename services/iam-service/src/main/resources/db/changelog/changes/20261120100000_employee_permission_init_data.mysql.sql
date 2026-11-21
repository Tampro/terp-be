-- liquibase formatted sql

-- changeset ERP-IAM:20261120100000-1
-- Permissions checked by employee-service; must match EmployeePermissions / EDefaultRole. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('EMPLOYEE_VIEW', 'EMPLOYEE', 'View employees, their profile and documents'),
       ('EMPLOYEE_CREATE', 'EMPLOYEE', 'Add employees and import them'),
       ('EMPLOYEE_UPDATE', 'EMPLOYEE', 'Edit employees, their documents, status and login'),
       ('EMPLOYEE_DELETE', 'EMPLOYEE', 'Delete employees'),
       ('EMPLOYEE_BANKING_VIEW', 'EMPLOYEE', 'See and edit bank account, tax payer id and base salary'),
       ('ORG_VIEW', 'ORG', 'View branches, departments, designations and document types'),
       ('ORG_MANAGE', 'ORG', 'Manage branches, departments, designations and document types')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261120100000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('EMPLOYEE_VIEW', 'EMPLOYEE_CREATE', 'EMPLOYEE_UPDATE', 'EMPLOYEE_DELETE',
                                         'EMPLOYEE_BANKING_VIEW', 'ORG_VIEW', 'ORG_MANAGE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
