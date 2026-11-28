-- liquibase formatted sql

-- changeset ERP-IAM:20261129180100-1
-- Coupons in order-service; must match CouponPermissions. Module ORDER (always on). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('COUPON_VIEW', 'ORDER', 'View coupons of Express orders'),
       ('COUPON_MANAGE', 'ORDER', 'Create, change, switch off and delete coupons')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261129180100-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('COUPON_VIEW', 'COUPON_MANAGE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
