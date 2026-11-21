-- liquibase formatted sql

-- changeset ERP-IAM:20261118130100-1
-- Company-side SaaS permissions; must match SaasPermissions. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('SAAS_SUBSCRIPTION_VIEW', 'SAAS', 'View the company subscription and offered plans'),
       ('SAAS_ORDER_VIEW', 'SAAS', 'View the company plan orders'),
       ('SAAS_ORDER_CREATE', 'SAAS', 'Order, price and cancel plan orders')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261118130100-2
-- Companies created before this change: give their COMPANY_ADMIN role the same SaaS permissions new companies get
-- from EDefaultRole (otherwise an expired company could not be renewed by its admin).
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('SAAS_SUBSCRIPTION_VIEW', 'SAAS_ORDER_VIEW', 'SAAS_ORDER_CREATE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
