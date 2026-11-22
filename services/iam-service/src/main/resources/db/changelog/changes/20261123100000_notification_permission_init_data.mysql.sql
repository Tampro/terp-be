-- liquibase formatted sql

-- changeset ERP-IAM:20261123100000-1
-- Delivery log of notification-service; must match NotificationPermissions. Module NOTIFICATION is always on
-- (EPlanModule.ALWAYS_ON). Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('NOTIFICATION_LOG_VIEW', 'NOTIFICATION', 'View the delivery log of email, Slack, Telegram and webhook notifications'),
       ('NOTIFICATION_LOG_MANAGE', 'NOTIFICATION', 'Retry failed notification deliveries')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261123100000-2
-- Every COMPANY_ADMIN gets them (EDefaultRole); existing companies too, otherwise editing that role fails IAM_012.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('NOTIFICATION_LOG_VIEW', 'NOTIFICATION_LOG_MANAGE')
WHERE r.code = 'COMPANY_ADMIN'
  AND r.deleted = 0;
