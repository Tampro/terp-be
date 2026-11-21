-- liquibase formatted sql

-- changeset ERP-IAM:20261118100100-1
-- Permission catalog of the IAM module; must match IamPermissions. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('IAM_ROLE_VIEW', 'IAM', 'View roles'),
       ('IAM_ROLE_CREATE', 'IAM', 'Create roles'),
       ('IAM_ROLE_UPDATE', 'IAM', 'Update roles and their permissions'),
       ('IAM_ROLE_DELETE', 'IAM', 'Delete roles'),
       ('IAM_USER_VIEW', 'IAM', 'View accounts'),
       ('IAM_USER_CREATE', 'IAM', 'Create accounts'),
       ('IAM_USER_UPDATE', 'IAM', 'Change roles and lock accounts'),
       ('IAM_PERMISSION_VIEW', 'IAM', 'View the permission catalog')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);
