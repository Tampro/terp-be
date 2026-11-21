-- liquibase formatted sql

-- changeset ERP-IAM:20261119090100-1
-- Permissions checked by media-service; must match EDefaultRole / MediaController. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('MEDIA_VIEW', 'MEDIA', 'View the media library'),
       ('MEDIA_UPLOAD', 'MEDIA', 'Upload files, rename and move them, manage folders'),
       ('MEDIA_DELETE', 'MEDIA', 'Delete files and folders')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261119090100-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('MEDIA_VIEW', 'MEDIA_UPLOAD', 'MEDIA_DELETE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
