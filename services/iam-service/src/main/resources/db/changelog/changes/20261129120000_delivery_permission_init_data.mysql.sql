-- liquibase formatted sql

-- changeset ERP-IAM:20261129120000-1
-- Deliveries in delivery-service; must match DeliveryPermissions. Module DELIVERY is always on (EPlanModule.ALWAYS_ON).
-- Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('DELIVERY_VIEW', 'DELIVERY', 'View deliveries of the company''s stores'),
       ('DELIVERY_MANAGE', 'DELIVERY', 'Assign riders to deliveries and cancel them'),
       ('DELIVERY_RIDE', 'DELIVERY', 'Deliver orders as a rider (own deliveries only)')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261129120000-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('DELIVERY_VIEW', 'DELIVERY_MANAGE', 'DELIVERY_RIDE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
