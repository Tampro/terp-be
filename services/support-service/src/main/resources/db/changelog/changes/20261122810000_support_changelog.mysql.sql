-- liquibase formatted sql

-- changeset ERP-SUPPORT:20261122810000-1
-- Ticket categories (reference ticket_categories: name, color).
CREATE TABLE ticket_category
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(255) NOT NULL,
    color        VARCHAR(7)   NOT NULL DEFAULT '#000000',
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_ticket_category_company (company_id, deleted, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-SUPPORT:20261122810000-2
-- Tickets (reference tickets). ticket_number TKT-000001 is per company (the reference uses time()); the tracking
-- token is stored only as its SHA-256 (the raw token is shown once, to whoever created the ticket). note = the
-- reference's internal note: never shown on the portal. attachments = JSON array of {fileId, name} (media-service).
CREATE TABLE ticket
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    ticket_number       VARCHAR(20)  NOT NULL,
    account_type        VARCHAR(10)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    category_id         BIGINT       NULL,
    subject             VARCHAR(255) NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    description         MEDIUMTEXT   NULL,
    attachments         TEXT         NULL,
    note                TEXT         NULL,
    tracking_token_hash CHAR(64)     NOT NULL,
    from_portal         BIT(1)       NOT NULL DEFAULT 0,
    deleted             BIT(1)       NOT NULL DEFAULT 0,
    created_by          VARCHAR(64)  NULL,
    updated_by          VARCHAR(64)  NULL,
    created_date        DATETIME(6)  NULL,
    updated_date        DATETIME(6)  NULL,
    UNIQUE KEY uk_ticket_number (company_id, ticket_number),
    UNIQUE KEY uk_ticket_tracking (tracking_token_hash),
    INDEX idx_ticket_company_status (company_id, deleted, status),
    INDEX idx_ticket_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-SUPPORT:20261122810000-3
-- Next ticket number per company; the row is locked (SELECT ... FOR UPDATE) while a ticket is numbered.
CREATE TABLE ticket_sequence
(
    company_id BIGINT NOT NULL PRIMARY KEY,
    next_value BIGINT NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-SUPPORT:20261122810000-4
-- Conversation of a ticket (reference support_ticket_conversions: sender admin / user, description, attachments).
CREATE TABLE ticket_reply
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id    BIGINT       NOT NULL,
    sender_type  VARCHAR(10)  NOT NULL,
    sender_name  VARCHAR(255) NULL,
    description  MEDIUMTEXT   NOT NULL,
    attachments  TEXT         NULL,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_ticket_reply_ticket (ticket_id, id),
    CONSTRAINT fk_ticket_reply_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-SUPPORT:20261122810000-5
-- Knowledge base categories (reference knowledge_base_categories: title).
CREATE TABLE kb_category
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    title        VARCHAR(255) NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_kb_category_company (company_id, deleted, title)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-SUPPORT:20261122810000-6
-- Knowledge base articles (reference support_ticket_knowledge_bases: title, description, category; the request also
-- takes content). published is not in the reference (TODO(assumption) in KbArticle).
CREATE TABLE kb_article
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    category_id  BIGINT       NOT NULL,
    title        VARCHAR(255) NOT NULL,
    description  VARCHAR(500) NOT NULL,
    content      MEDIUMTEXT   NULL,
    published    BIT(1)       NOT NULL DEFAULT 0,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_kb_article_company (company_id, deleted, published, category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-SUPPORT:20261122810000-7
-- FAQs (reference support_ticket_faqs: title = question, description = answer).
CREATE TABLE faq
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    title        VARCHAR(255) NOT NULL,
    description  MEDIUMTEXT   NOT NULL,
    published    BIT(1)       NOT NULL DEFAULT 0,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_faq_company (company_id, deleted, published)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-SUPPORT:20261122810000-8
-- Contact messages from the portal (reference support_contacts: name, first/last name, email, subject, message).
CREATE TABLE contact_message
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT        NOT NULL,
    name         VARCHAR(255)  NOT NULL,
    email        VARCHAR(255)  NOT NULL,
    subject      VARCHAR(255)  NOT NULL,
    message      VARCHAR(5000) NOT NULL,
    deleted      BIT(1)        NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    updated_by   VARCHAR(64)   NULL,
    created_date DATETIME(6)   NULL,
    updated_date DATETIME(6)   NULL,
    INDEX idx_contact_message_company (company_id, deleted, created_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-SUPPORT:20261122810000-9
-- Portal settings, one row per company (reference support_ticket_settings key/value: brand, title_sections,
-- cta_sections, support_information, contact_information, plus support_ticket_quick_links). content = JSON of
-- PortalSettingsService.PortalContent. slug is unique across companies, like the career page slug.
CREATE TABLE portal_settings
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    slug         VARCHAR(50)  NOT NULL,
    enabled      BIT(1)       NOT NULL DEFAULT 0,
    brand_name   VARCHAR(150) NOT NULL,
    logo_url     VARCHAR(500) NULL,
    favicon_url  VARCHAR(500) NULL,
    content      MEDIUMTEXT   NULL,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    UNIQUE KEY uk_portal_settings_company (company_id),
    UNIQUE KEY uk_portal_settings_slug (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-SUPPORT:20261122810000-10
-- Custom pages of the portal (reference support_ticket_custom_pages: title, slug, contents, description,
-- enable_page_footer). The slug is unique within a company (the reference makes it unique globally).
CREATE TABLE portal_page
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    title        VARCHAR(255) NOT NULL,
    slug         VARCHAR(100) NOT NULL,
    contents     MEDIUMTEXT   NOT NULL,
    description  VARCHAR(500) NULL,
    show_in_footer BIT(1)     NOT NULL DEFAULT 1,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_portal_page_company (company_id, deleted, slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
