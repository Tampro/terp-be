-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120190000-1
-- Document Types page (docs/analysis/06 §6.3.7): description and Required/Optional. For a company type, required is
-- the company's choice; for a system type it is the default until the company sets its own (document_type_setting).
ALTER TABLE document_type
    ADD COLUMN description VARCHAR(500) NULL AFTER name,
    ADD COLUMN required    BIT(1)       NOT NULL DEFAULT 0 AFTER description;

-- changeset ERP-EMPLOYEE:20261120190000-2
-- A company's Required/Optional choice for a system document type (system rows stay shared and read-only).
CREATE TABLE document_type_setting
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT      NOT NULL,
    document_type_id BIGINT      NOT NULL,
    required         BIT(1)      NOT NULL,
    created_by       VARCHAR(64) NULL,
    updated_by       VARCHAR(64) NULL,
    created_date     DATETIME(6) NULL,
    updated_date     DATETIME(6) NULL,
    UNIQUE KEY uk_document_type_setting (company_id, document_type_id),
    CONSTRAINT fk_document_type_setting_type FOREIGN KEY (document_type_id) REFERENCES document_type (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
