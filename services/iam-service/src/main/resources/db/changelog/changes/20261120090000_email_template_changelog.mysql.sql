-- liquibase formatted sql

-- changeset ERP-IAM:20261120090000-1
-- Email templates (docs/analysis/06 §3.3.1, WorkDo email_templates / email_template_langs / user_email_templates).
-- The set of templates and their variables is fixed in code (EEmailTemplate); the texts are edited here.
CREATE TABLE email_template
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    from_name    VARCHAR(100) NULL,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    CONSTRAINT uk_email_template_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261120090000-2
CREATE TABLE email_template_content
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id  BIGINT       NOT NULL,
    lang         VARCHAR(5)   NOT NULL,
    subject      VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    CONSTRAINT uk_email_template_content UNIQUE (template_id, lang),
    CONSTRAINT fk_etc_template FOREIGN KEY (template_id) REFERENCES email_template (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261120090000-3
-- Company-side on/off per template. No row = on.
CREATE TABLE company_email_notification
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT      NOT NULL,
    template_code VARCHAR(50) NOT NULL,
    enabled       BIT(1)      NOT NULL,
    created_by    VARCHAR(64) NULL,
    updated_by    VARCHAR(64) NULL,
    created_date  DATETIME(6) NULL,
    updated_date  DATETIME(6) NULL,
    CONSTRAINT uk_company_email_notification UNIQUE (company_id, template_code),
    CONSTRAINT fk_cen_company FOREIGN KEY (company_id) REFERENCES company (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
