-- liquibase formatted sql

-- changeset ERP-IAM:20261121320100-1
-- Company side of the platform helpdesk; managing it is super-admin only (no code). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('HELPDESK_VIEW', 'HELPDESK', 'See the company''s tickets to the platform helpdesk'),
       ('HELPDESK_CREATE', 'HELPDESK', 'Open tickets to the platform helpdesk and reply to them')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261121320100-2
-- Every COMPANY_ADMIN holds them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('HELPDESK_VIEW', 'HELPDESK_CREATE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
