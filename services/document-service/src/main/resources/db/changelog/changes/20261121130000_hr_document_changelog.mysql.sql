-- liquibase formatted sql

-- changeset ERP-DOCUMENT:20261121130000-1
-- HR documents (docs/analysis/06 9.4.1): company policies and forms; the file is a media library file (media-service).
-- status is the lifecycle stage set by the admin; a document past its expiry date (not archived) reads as EXPIRED.
CREATE TABLE hr_document
(
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT        NOT NULL,
    category_id             BIGINT        NOT NULL,
    title                   VARCHAR(200)  NOT NULL,
    description             VARCHAR(2000) NULL,
    file_id                 BIGINT        NOT NULL,
    effective_date          DATE          NULL,
    expiry_date             DATE          NULL,
    requires_acknowledgment BIT(1)        NOT NULL DEFAULT 0,
    status                  VARCHAR(12)   NOT NULL DEFAULT 'DRAFT',
    deleted                 BIT(1)        NOT NULL DEFAULT 0,
    created_by              VARCHAR(64)   NULL,
    created_date            DATETIME(6)   NULL,
    updated_by              VARCHAR(64)   NULL,
    updated_date            DATETIME(6)   NULL,
    INDEX idx_hr_document_company (company_id, status),
    INDEX idx_hr_document_category (category_id),
    CONSTRAINT fk_hr_document_category FOREIGN KEY (category_id) REFERENCES document_category (id)
);

-- changeset ERP-DOCUMENT:20261121130000-2
-- Acknowledgments (docs/analysis/06 9.4.3): a document an employee must acknowledge by a due date. The employee's IAM
-- user id is copied so the employee can acknowledge it themself; Overdue (pending past the due date) is computed.
CREATE TABLE document_acknowledgment
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT        NOT NULL,
    document_id       BIGINT        NOT NULL,
    employee_id       BIGINT        NOT NULL,
    employee_user_id  BIGINT        NULL,
    employee_name     VARCHAR(200)  NULL,
    employee_email    VARCHAR(255)  NULL,
    due_date          DATE          NULL,
    note              VARCHAR(1000) NULL,
    status            VARCHAR(12)   NOT NULL DEFAULT 'PENDING',
    assigned_by       VARCHAR(64)   NULL,
    assigned_by_name  VARCHAR(200)  NULL,
    acknowledged_at   DATETIME(6)   NULL,
    acknowledged_by   VARCHAR(64)   NULL,
    acknowledge_note  VARCHAR(1000) NULL,
    deleted           BIT(1)        NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)   NULL,
    created_date      DATETIME(6)   NULL,
    updated_by        VARCHAR(64)   NULL,
    updated_date      DATETIME(6)   NULL,
    INDEX idx_document_ack_company (company_id, status, due_date),
    INDEX idx_document_ack_document (document_id),
    INDEX idx_document_ack_user (employee_user_id),
    CONSTRAINT fk_document_ack_document FOREIGN KEY (document_id) REFERENCES hr_document (id)
);
