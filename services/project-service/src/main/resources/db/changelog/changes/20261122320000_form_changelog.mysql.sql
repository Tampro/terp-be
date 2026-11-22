-- liquibase formatted sql

-- changeset ERP-PROJECT:20261122320000-1
-- Form builder (ERP manual 11.2). public_token is the only key of the public link (>= 256-bit random, URL-safe):
-- unique among all companies, regenerated on demand so an old link stops working.
CREATE TABLE form
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(255) NOT NULL,
    active       BIT(1)       NOT NULL DEFAULT 1,
    layout       VARCHAR(20)  NOT NULL DEFAULT 'SINGLE_COLUMN',
    public_token VARCHAR(64)  NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    UNIQUE KEY uk_form_public_token (public_token),
    INDEX idx_form_company (company_id, deleted, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-PROJECT:20261122320000-2
-- Fields are replaced as a whole on edit; field_key stays stable when the editor sends it back, so answers and
-- convert mappings keep pointing at the same field. options = JSON array of strings (SELECT/RADIO/CHECKBOX only).
CREATE TABLE form_field
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    form_id      BIGINT       NOT NULL,
    field_key    VARCHAR(40)  NOT NULL,
    label        VARCHAR(255) NOT NULL,
    field_type   VARCHAR(20)  NOT NULL,
    required     BIT(1)       NOT NULL DEFAULT 0,
    placeholder  VARCHAR(255) NULL,
    options      TEXT         NULL,
    position     INT          NOT NULL,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    UNIQUE KEY uk_form_field_key (form_id, field_key),
    INDEX idx_form_field_form (form_id, position)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-PROJECT:20261122320000-3
-- answers = JSON snapshot [{key,label,type,value}] taken at submit time, so later form edits do not rewrite history.
-- PASSWORD answers are stored only as a salted SHA-256 hash. converted_target_id is an id in another service
-- (project here, lead/deal/contract in crm-service): no cross-database key.
CREATE TABLE form_response
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id            BIGINT      NOT NULL,
    form_id               BIGINT      NOT NULL,
    answers               MEDIUMTEXT  NOT NULL,
    converted_target_type VARCHAR(20) NULL,
    converted_target_id   VARCHAR(40) NULL,
    converted_at          DATETIME(6) NULL,
    deleted               BIT(1)      NOT NULL DEFAULT 0,
    created_by            VARCHAR(64) NULL,
    updated_by            VARCHAR(64) NULL,
    created_date          DATETIME(6) NULL,
    updated_date          DATETIME(6) NULL,
    INDEX idx_form_response_form (form_id, deleted, created_date),
    INDEX idx_form_response_company (company_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
