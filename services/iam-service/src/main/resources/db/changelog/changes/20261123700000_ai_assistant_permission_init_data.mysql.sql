-- liquibase formatted sql

-- changeset ERP-IAM:20261123700000-1
-- AI assistant (ERP manual 19); must match AiPermissions. Module AI is governed by the plan module AI_ASSISTANT
-- (EPlanModule). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('AI_ASSISTANT_USE', 'AI', 'Draft texts with the AI assistant (sends the typed text to the company AI provider)')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261123700000-2
-- TODO(assumption): COMPANY_ADMIN, MANAGER and HR use the assistant (EDefaultRole) — confirm with PO. Existing
-- companies too, otherwise editing COMPANY_ADMIN fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code = 'AI_ASSISTANT_USE'
WHERE r.code IN ('COMPANY_ADMIN', 'MANAGER', 'HR')
  AND r.deleted = 0;
