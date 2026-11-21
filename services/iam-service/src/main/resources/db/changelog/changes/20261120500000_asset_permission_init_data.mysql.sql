-- liquibase formatted sql

-- changeset ERP-IAM:20261120500000-1
-- Asset Management (docs/analysis/06 §8.2), checked by asset-service; must match AssetPermissions
-- (PO, 2026-11-20). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('ASSET_VIEW', 'ASSET', 'View asset types, assets, assignments, maintenance and depreciation'),
       ('ASSET_MANAGE', 'ASSET', 'Manage asset types and assets, assign, return and schedule maintenance')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261120500000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('ASSET_VIEW', 'ASSET_MANAGE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
