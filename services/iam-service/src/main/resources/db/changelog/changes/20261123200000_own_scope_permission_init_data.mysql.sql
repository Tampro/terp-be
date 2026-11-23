-- liquibase formatted sql

-- changeset ERP-IAM:20261123200000-1
-- "Own records only" variants (OwnPermissions) and the client / vendor portal (PortalPermissions). Each _OWN code keeps
-- the module of its base code, so the same plan module governs it; PORTAL is always on (EPlanModule.ALWAYS_ON).
-- Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('SALES_VIEW_OWN', 'SALES', 'View own proposals, sales invoices and sales returns'),
       ('SALES_MANAGE_OWN', 'SALES', 'Create and edit own proposals, invoices and returns'),
       ('QUOTATION_VIEW_OWN', 'QUOTATION', 'View own quotations'),
       ('QUOTATION_MANAGE_OWN', 'QUOTATION', 'Create, edit, send, version and convert own quotations'),
       ('PURCHASE_VIEW_OWN', 'PURCHASE', 'View own purchase invoices and purchase returns'),
       ('PURCHASE_MANAGE_OWN', 'PURCHASE', 'Create, edit and delete own draft purchase invoices and returns'),
       ('CRM_VIEW_OWN', 'CRM', 'View own or assigned leads and deals and their activity'),
       ('CRM_MANAGE_OWN', 'CRM', 'Manage own or assigned leads and deals; convert own leads'),
       ('CRM_CONTRACT_VIEW_OWN', 'CRM_CONTRACT', 'View own customer contracts'),
       ('CRM_CONTRACT_MANAGE_OWN', 'CRM_CONTRACT', 'Create, edit, renew and close own customer contracts'),
       ('PROJECT_VIEW_OWN', 'PROJECT', 'View projects the user is a member of, their tasks and bugs'),
       ('PROJECT_MANAGE_OWN', 'PROJECT', 'Edit projects the user created or leads'),
       ('PROJECT_TASK_MANAGE_OWN', 'PROJECT', 'Create, edit, move and comment tasks of own projects / assigned tasks'),
       ('SUPPORT_TICKET_VIEW_OWN', 'SUPPORT', 'View own support tickets and their conversation'),
       ('SUPPORT_TICKET_MANAGE_OWN', 'SUPPORT', 'Create and reply to own support tickets'),
       ('PORTAL_CLIENT', 'PORTAL', 'Client portal: own invoices, payments, credit notes, contracts, projects'),
       ('PORTAL_VENDOR', 'PORTAL', 'Vendor portal: own purchase invoices, payments, debit notes')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261123200000-2
-- COMPANY_ADMIN holds every code (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('SALES_VIEW_OWN', 'SALES_MANAGE_OWN', 'QUOTATION_VIEW_OWN',
                                         'QUOTATION_MANAGE_OWN', 'PURCHASE_VIEW_OWN', 'PURCHASE_MANAGE_OWN',
                                         'CRM_VIEW_OWN', 'CRM_MANAGE_OWN', 'CRM_CONTRACT_VIEW_OWN',
                                         'CRM_CONTRACT_MANAGE_OWN', 'PROJECT_VIEW_OWN', 'PROJECT_MANAGE_OWN',
                                         'PROJECT_TASK_MANAGE_OWN', 'SUPPORT_TICKET_VIEW_OWN',
                                         'SUPPORT_TICKET_MANAGE_OWN', 'PORTAL_CLIENT', 'PORTAL_VENDOR')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;

-- changeset ERP-IAM:20261123200000-3
-- MANAGER: every _OWN code (EDefaultRole, TODO(assumption) there).
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code LIKE '%\_OWN'
WHERE r.code = 'MANAGER'
  AND r.deleted = 0;

-- changeset ERP-IAM:20261123200000-4
-- EMPLOYEE, EXECUTIVE, CLIENT, VENDOR of existing companies (EDefaultRole for new companies).
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p
              ON (r.code = 'EMPLOYEE' AND p.code IN ('PROJECT_VIEW_OWN', 'PROJECT_TASK_MANAGE_OWN',
                                                     'SUPPORT_TICKET_VIEW_OWN', 'SUPPORT_TICKET_MANAGE_OWN'))
                  OR (r.code = 'EXECUTIVE' AND p.code IN ('SALES_VIEW_OWN', 'QUOTATION_VIEW_OWN', 'CRM_VIEW_OWN',
                                                          'CRM_CONTRACT_VIEW_OWN', 'PROJECT_VIEW_OWN'))
                  OR (r.code = 'CLIENT' AND p.code IN ('PORTAL_CLIENT', 'SUPPORT_TICKET_VIEW_OWN',
                                                       'SUPPORT_TICKET_MANAGE_OWN'))
                  OR (r.code = 'VENDOR' AND p.code = 'PORTAL_VENDOR')
WHERE r.code IN ('EMPLOYEE', 'EXECUTIVE', 'CLIENT', 'VENDOR')
  AND r.deleted = 0;
