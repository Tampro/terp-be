-- liquibase formatted sql

-- changeset ERP-IAM:20261121090000-1
-- Events (docs/analysis/06 §9.2), checked by meeting-service; must match EventPermissions (PO, 2026-11-21).
-- Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('EVENT_VIEW', 'EVENT', 'View event types, events and the event calendar'),
       ('EVENT_MANAGE', 'EVENT', 'Manage event types and events'),
       ('EVENT_APPROVE', 'EVENT', 'Approve or reject pending events')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261121090000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('EVENT_VIEW', 'EVENT_MANAGE', 'EVENT_APPROVE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
