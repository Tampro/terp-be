-- liquibase formatted sql

-- changeset ERP-IAM:20261123500000-1
-- A company's own text for one business notification (ERP manual 3.5 "Email templates" / "Notification templates"):
-- channel EMAIL (template_key = ENotificationEvent code, subject + sanitised HTML body) or SLACK / TELEGRAM / SMS
-- (template_key = EChannelEvent routing key, body = plain text, subject NULL), per language. No row = the built-in
-- default text of notification-service. Mirrors TERP Core notification_template_langs (parent_id, lang, module,
-- content, variables) with company_id added: the reference keeps one global text per language.
CREATE TABLE company_notification_template
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    channel      VARCHAR(10)  NOT NULL,
    template_key VARCHAR(100) NOT NULL,
    lang         VARCHAR(5)   NOT NULL,
    subject      VARCHAR(255) NULL,
    body         MEDIUMTEXT   NOT NULL,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    UNIQUE KEY uk_company_notification_template (company_id, channel, template_key, lang)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
