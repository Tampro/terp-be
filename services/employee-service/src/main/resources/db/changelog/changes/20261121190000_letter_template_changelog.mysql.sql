-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261121190000-1
-- Date of leaving ({leaving_date} of the Experience Certificate, docs/analysis/06 §10.5.9); optional.
ALTER TABLE employee
    ADD COLUMN date_of_leaving DATE NULL AFTER date_of_joining;

-- changeset ERP-EMPLOYEE:20261121190000-2
-- A company's own letter text per type and language (§10.5.8-10.5.10); no row = the built-in default.
CREATE TABLE letter_template
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT      NOT NULL,
    letter_type  VARCHAR(30) NOT NULL,
    lang         VARCHAR(5)  NOT NULL,
    content      MEDIUMTEXT  NOT NULL,
    created_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL,
    CONSTRAINT uk_letter_template UNIQUE (company_id, letter_type, lang)
);
