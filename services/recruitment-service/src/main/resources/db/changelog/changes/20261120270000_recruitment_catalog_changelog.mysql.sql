-- liquibase formatted sql

-- changeset ERP-RECRUITMENT:20261120270000-1
-- Job categories, job types, candidate sources and interview types (docs/analysis/06 §7.1.10, 7.1.11, 7.1.13, 7.1.14;
-- doc 03 `job_categories`, `job_types`, `candidate_sources`, `interview_types`): the same name / description / status
-- shape, one table told apart by kind. company_id is the IAM company id (another database: no foreign key).
CREATE TABLE recruitment_catalog
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT        NOT NULL,
    kind         VARCHAR(20)   NOT NULL,
    name         VARCHAR(150)  NOT NULL,
    description  VARCHAR(1000) NULL,
    status       VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    deleted      BIT(1)        NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    updated_by   VARCHAR(64)   NULL,
    created_date DATETIME(6)   NULL,
    updated_date DATETIME(6)   NULL,
    INDEX idx_recruitment_catalog_company_kind (company_id, kind)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-RECRUITMENT:20261120270000-2
-- Job locations (§7.1.12, doc 03 `job_locations`): an office address, or a remote setup.
CREATE TABLE job_location
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(150) NOT NULL,
    remote       BIT(1)       NOT NULL DEFAULT 0,
    address      VARCHAR(255) NULL,
    city         VARCHAR(100) NULL,
    state        VARCHAR(100) NULL,
    country      VARCHAR(100) NULL,
    postal_code  VARCHAR(20)  NULL,
    status       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_job_location_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-RECRUITMENT:20261120270000-3
-- Custom questions (§7.1.17, doc 03 `custom_questions`) asked in applications of the postings that use them.
CREATE TABLE custom_question
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT        NOT NULL,
    question     VARCHAR(1000) NOT NULL,
    required     BIT(1)        NOT NULL DEFAULT 0,
    deleted      BIT(1)        NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    updated_by   VARCHAR(64)   NULL,
    created_date DATETIME(6)   NULL,
    updated_date DATETIME(6)   NULL,
    INDEX idx_custom_question_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
