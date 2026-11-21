-- liquibase formatted sql

-- changeset ERP-IAM:20261120520000-1
-- Meetings (docs/analysis/06 §9.1), checked by meeting-service; must match MeetingPermissions (PO, 2026-11-20).
-- Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('MEETING_VIEW', 'MEETING', 'View meeting types, rooms, meetings and action items'),
       ('MEETING_MANAGE', 'MEETING', 'Manage meeting types and rooms, schedule meetings and manage action items')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261120520000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('MEETING_VIEW', 'MEETING_MANAGE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
