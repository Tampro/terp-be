-- liquibase formatted sql

-- changeset ERP-RECRUITMENT:20261120330000-1
-- Public career page of a company (docs/analysis/06 §7.1.9): the company admin picks the slug of
-- /api/v1/public/career/{slug} and turns the page on (PO, 2026-11-20).
CREATE TABLE career_settings
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    slug         VARCHAR(50)  NOT NULL,
    enabled      BIT(1)       NOT NULL DEFAULT 0,
    display_name VARCHAR(150) NOT NULL,
    headline     VARCHAR(200) NULL,
    tagline      VARCHAR(500) NULL,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    CONSTRAINT uk_career_settings_company UNIQUE (company_id),
    CONSTRAINT uk_career_settings_slug UNIQUE (slug)
);

-- changeset ERP-RECRUITMENT:20261120330000-2
-- Optional cover letter file of an application, kept next to the resume.
ALTER TABLE candidate
    ADD COLUMN cover_letter_key  VARCHAR(64)  NULL AFTER resume_size,
    ADD COLUMN cover_letter_name VARCHAR(255) NULL AFTER cover_letter_key,
    ADD COLUMN cover_letter_mime VARCHAR(127) NULL AFTER cover_letter_name,
    ADD COLUMN cover_letter_size BIGINT       NULL AFTER cover_letter_mime;

-- changeset ERP-RECRUITMENT:20261120330000-3
-- Branch / department names copied when an admin saves or publishes the posting: the public career page has no token
-- to ask employee-service.
ALTER TABLE job_posting
    ADD COLUMN branch_name     VARCHAR(150) NULL AFTER department_id,
    ADD COLUMN department_name VARCHAR(150) NULL AFTER branch_name;
