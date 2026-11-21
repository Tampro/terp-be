-- liquibase formatted sql

-- changeset ERP-IAM:20261121350000-1
-- CRM and customer contracts (ERP manual 7), checked by crm-service; must match SalesPermissions.CRM / CRM_CONTRACT.
-- Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('CRM_VIEW', 'CRM', 'View pipelines, leads, deals, their activity and CRM reports'),
       ('CRM_MANAGE', 'CRM', 'Manage pipelines, stages, labels, sources, leads and deals; convert leads'),
       ('CRM_CONTRACT_VIEW', 'CRM_CONTRACT', 'View customer contracts, types and settings'),
       ('CRM_CONTRACT_MANAGE', 'CRM_CONTRACT', 'Create, edit, renew and close customer contracts; manage types')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261121350000-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('CRM_VIEW', 'CRM_MANAGE', 'CRM_CONTRACT_VIEW', 'CRM_CONTRACT_MANAGE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
