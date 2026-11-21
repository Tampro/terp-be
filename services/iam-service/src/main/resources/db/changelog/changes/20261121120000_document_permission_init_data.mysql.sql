-- liquibase formatted sql

-- changeset ERP-IAM:20261121120000-1
-- Documents & Contracts (docs/analysis/06 §9.4), checked by document-service; must match DocumentPermissions
-- (PO, 2026-11-21). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('DOCUMENT_VIEW', 'DOCUMENT', 'View document categories, HR documents, acknowledgments and document templates'),
       ('DOCUMENT_MANAGE', 'DOCUMENT', 'Manage document categories, HR documents, acknowledgments and document templates'),
       ('CONTRACT_VIEW', 'CONTRACT', 'View contract types, employee contracts and contract templates'),
       ('CONTRACT_MANAGE', 'CONTRACT', 'Manage contract types, employee contracts and contract templates')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261121120000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('DOCUMENT_VIEW', 'DOCUMENT_MANAGE', 'CONTRACT_VIEW', 'CONTRACT_MANAGE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
