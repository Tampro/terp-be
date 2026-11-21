-- liquibase formatted sql

-- changeset ERP-IAM:20261121100000-1
-- Zoom meetings (docs/analysis/06 §9.3), checked by meeting-service; must match ZoomPermissions (PO, 2026-11-21).
-- Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('ZOOM_VIEW', 'ZOOM', 'View Zoom meetings and join links'),
       ('ZOOM_MANAGE', 'ZOOM', 'Manage Zoom settings and meetings, see start links and passcodes')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261121100000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('ZOOM_VIEW', 'ZOOM_MANAGE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
