-- liquibase formatted sql

-- changeset ERP-IAM:20261122800000-1
-- Support & communication (ERP manual 18) in support-service; must match SupportPermissions. Module SUPPORT is
-- governed by the plan module SUPPORT_TICKET; MESSENGER is always on (EPlanModule.ALWAYS_ON). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('SUPPORT_TICKET_VIEW', 'SUPPORT', 'View support tickets, their conversation and ticket categories'),
       ('SUPPORT_TICKET_MANAGE', 'SUPPORT', 'Create, edit, reply to, change the status of and delete tickets; manage ticket categories'),
       ('SUPPORT_KB_VIEW', 'SUPPORT', 'View knowledge base articles, categories and FAQs'),
       ('SUPPORT_KB_MANAGE', 'SUPPORT', 'Create, edit, delete and import knowledge base articles, categories and FAQs'),
       ('SUPPORT_CONTACT_VIEW', 'SUPPORT', 'View contact messages sent from the support portal'),
       ('SUPPORT_CONTACT_MANAGE', 'SUPPORT', 'Delete contact messages'),
       ('SUPPORT_SETTINGS_MANAGE', 'SUPPORT', 'Configure the support portal: slug, branding, sections, custom pages'),
       ('MESSENGER_USE', 'MESSENGER', 'Chat one-to-one with users of the same company')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261122800000-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('SUPPORT_TICKET_VIEW', 'SUPPORT_TICKET_MANAGE', 'SUPPORT_KB_VIEW',
                                         'SUPPORT_KB_MANAGE', 'SUPPORT_CONTACT_VIEW', 'SUPPORT_CONTACT_MANAGE',
                                         'SUPPORT_SETTINGS_MANAGE', 'MESSENGER_USE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;

-- changeset ERP-IAM:20261122800000-3
-- Messenger for the HR and EMPLOYEE roles of existing companies (EDefaultRole for new companies).
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code = 'MESSENGER_USE'
WHERE r.code IN ('HR', 'EMPLOYEE')
  AND r.deleted = 0;
