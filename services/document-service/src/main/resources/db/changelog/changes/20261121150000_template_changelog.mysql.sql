-- liquibase formatted sql

-- changeset ERP-DOCUMENT:20261121150000-1
-- Contract and document templates (docs/analysis/06 9.4.4, 9.4.5) in one table: kind CONTRACT belongs to a contract
-- type, kind DOCUMENT to a document category. Content uses {{variable_name}}; variables = the declared names (comma
-- list) plus those found in the content; default_values = a JSON object (document templates). At most one default per
-- kind and contract type / category (kept by the service).
CREATE TABLE doc_template
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT        NOT NULL,
    kind             VARCHAR(10)   NOT NULL,
    name             VARCHAR(200)  NOT NULL,
    description      VARCHAR(1000) NULL,
    contract_type_id BIGINT        NULL,
    category_id      BIGINT        NULL,
    file_format      VARCHAR(10)   NOT NULL DEFAULT 'PDF',
    status           VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    is_default       BIT(1)        NOT NULL DEFAULT 0,
    content          MEDIUMTEXT    NOT NULL,
    variables        VARCHAR(4000) NULL,
    default_values   TEXT          NULL,
    deleted          BIT(1)        NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_date     DATETIME(6)   NULL,
    updated_by       VARCHAR(64)   NULL,
    updated_date     DATETIME(6)   NULL,
    INDEX idx_doc_template_company (company_id, kind, status),
    CONSTRAINT fk_doc_template_contract_type FOREIGN KEY (contract_type_id) REFERENCES contract_type (id),
    CONSTRAINT fk_doc_template_category FOREIGN KEY (category_id) REFERENCES document_category (id)
);

-- changeset ERP-DOCUMENT:20261121150000-2
-- The generated contract PDF attached to a contract (media library file id).
ALTER TABLE employee_contract
    ADD COLUMN document_file_id BIGINT NULL;
