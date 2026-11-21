-- liquibase formatted sql

-- changeset ERP-DOCUMENT:20261121120000-1
-- Document categories (docs/analysis/06 9.4.7): name unique per company (checked by the service); a mandatory category
-- makes its documents require acknowledgment by default.
CREATE TABLE document_category
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT        NOT NULL,
    name         VARCHAR(150)  NOT NULL,
    description  VARCHAR(1000) NULL,
    color        VARCHAR(7)    NOT NULL DEFAULT '#3B82F6',
    icon         VARCHAR(30)   NOT NULL DEFAULT 'folder',
    mandatory    BIT(1)        NOT NULL DEFAULT 0,
    status       VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    deleted      BIT(1)        NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_date DATETIME(6)   NULL,
    updated_by   VARCHAR(64)   NULL,
    updated_date DATETIME(6)   NULL,
    INDEX idx_document_category_company (company_id)
);

-- changeset ERP-DOCUMENT:20261121120000-2
-- Contract types (docs/analysis/06 9.4.6): standard terms; default duration null = open-ended.
CREATE TABLE contract_type
(
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT        NOT NULL,
    name                    VARCHAR(150)  NOT NULL,
    description             VARCHAR(1000) NULL,
    default_duration_months INT           NULL,
    probation_months        INT           NOT NULL DEFAULT 0,
    notice_days             INT           NOT NULL DEFAULT 0,
    renewable               BIT(1)        NOT NULL DEFAULT 0,
    status                  VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    deleted                 BIT(1)        NOT NULL DEFAULT 0,
    created_by              VARCHAR(64)   NULL,
    created_date            DATETIME(6)   NULL,
    updated_by              VARCHAR(64)   NULL,
    updated_date            DATETIME(6)   NULL,
    INDEX idx_contract_type_company (company_id)
);
