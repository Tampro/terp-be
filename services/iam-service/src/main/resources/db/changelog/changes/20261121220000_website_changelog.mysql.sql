-- liquibase formatted sql

-- changeset ERP-IAM:20261121220000-1
-- Landing Page settings (docs/analysis/06 §3.2.7): one JSON document per section, shaped by the FE.
CREATE TABLE landing_section
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    section_key  VARCHAR(30) NOT NULL,
    content      MEDIUMTEXT  NOT NULL,
    created_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL,
    CONSTRAINT uk_landing_section UNIQUE (section_key)
);

-- changeset ERP-IAM:20261121220000-2
-- Custom Pages (§3.2.8): sanitized HTML, public at /pages/{slug} once published.
CREATE TABLE custom_page
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(200) NOT NULL,
    slug             VARCHAR(220) NOT NULL,
    content          MEDIUMTEXT   NOT NULL,
    meta_title       VARCHAR(200) NULL,
    meta_description VARCHAR(500) NULL,
    sort_order       INT          NOT NULL DEFAULT 0,
    published        BIT(1)       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NULL,
    created_date     DATETIME(6)  NULL,
    updated_by       VARCHAR(64)  NULL,
    updated_date     DATETIME(6)  NULL,
    CONSTRAINT uk_custom_page_slug UNIQUE (slug)
);

-- changeset ERP-IAM:20261121220000-3
-- Contact Inquiries (§3.2.9) from the landing page's contact form.
CREATE TABLE contact_inquiry
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(150)  NOT NULL,
    email           VARCHAR(255)  NOT NULL,
    phone           VARCHAR(30)   NULL,
    subject         VARCHAR(255)  NOT NULL,
    message         TEXT          NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    last_replied_at DATETIME(6)   NULL,
    created_by      VARCHAR(64)   NULL,
    created_date    DATETIME(6)   NULL,
    updated_by      VARCHAR(64)   NULL,
    updated_date    DATETIME(6)   NULL,
    INDEX idx_contact_inquiry_status (status, created_date)
);

-- changeset ERP-IAM:20261121220000-4
-- Newsletter subscribers (§3.2.10).
CREATE TABLE newsletter_subscriber
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    email        VARCHAR(255) NOT NULL,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    CONSTRAINT uk_newsletter_email UNIQUE (email)
);
