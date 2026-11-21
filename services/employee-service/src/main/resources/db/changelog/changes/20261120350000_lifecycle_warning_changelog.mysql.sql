-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120350000-1
-- Company-managed lists of the lifecycle pages (PO, 2026-11-20): warning types and severities (§7.2.4), termination
-- types (§7.2.6), complaint and resolution types (§7.2.8). Names unique per company and kind (case-insensitive).
CREATE TABLE lifecycle_catalog
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    kind         VARCHAR(20)  NOT NULL,
    name         VARCHAR(150) NOT NULL,
    description  VARCHAR(500) NULL,
    status       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_lifecycle_catalog_company (company_id, kind)
);

-- changeset ERP-EMPLOYEE:20261120350000-2
-- Warnings (§7.2.4); warned_by = the manager issuing it (an employee of the company); improvement_plan_* optional.
-- Long texts are TEXT: four VARCHAR(5000) utf8mb4 columns exceed MySQL's 65535-byte row limit.
CREATE TABLE employee_warning
(
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id               BIGINT        NOT NULL,
    employee_id              BIGINT        NOT NULL,
    warned_by_id             BIGINT        NULL,
    warning_type_id          BIGINT        NOT NULL,
    severity_id              BIGINT        NOT NULL,
    subject                  VARCHAR(200)  NOT NULL,
    warning_date             DATE          NOT NULL,
    description TEXT NULL,
    document_media_id        BIGINT        NULL,
    expiry_date              DATE          NULL,
    status                   VARCHAR(12)   NOT NULL DEFAULT 'DRAFT',
    acknowledgment_date      DATE          NULL,
    employee_response TEXT NULL,
    has_improvement_plan     BIT(1)        NOT NULL DEFAULT 0,
    improvement_plan_goals TEXT NULL,
    improvement_plan_start   DATE          NULL,
    improvement_plan_end     DATE          NULL,
    improvement_plan_progress TEXT NULL,
    deleted                  BIT(1)        NOT NULL DEFAULT 0,
    created_by               VARCHAR(64)   NULL,
    created_date             DATETIME(6)   NULL,
    updated_by               VARCHAR(64)   NULL,
    updated_date             DATETIME(6)   NULL,
    CONSTRAINT fk_warning_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_warning_type FOREIGN KEY (warning_type_id) REFERENCES lifecycle_catalog (id),
    CONSTRAINT fk_warning_severity FOREIGN KEY (severity_id) REFERENCES lifecycle_catalog (id),
    INDEX idx_warning_company (company_id, status)
);
