-- liquibase formatted sql

-- changeset ERP-IAM:20261121210000-1
-- Language Management (docs/analysis/06 §3): languages of the UI and their translated labels. vi/en/ko are system
-- languages (the API and email texts exist in them); languages added later translate the UI labels only.
CREATE TABLE language
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(10)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    country_code VARCHAR(2)   NULL,
    enabled      BIT(1)       NOT NULL DEFAULT 1,
    system_lang  BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    CONSTRAINT uk_language_code UNIQUE (code)
);

-- changeset ERP-IAM:20261121210000-2
CREATE TABLE language_label
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    language_id  BIGINT       NOT NULL,
    label_key    VARCHAR(200) NOT NULL,
    label_value  TEXT         NOT NULL,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    CONSTRAINT uk_language_label UNIQUE (language_id, label_key),
    CONSTRAINT fk_language_label_language FOREIGN KEY (language_id) REFERENCES language (id)
);

-- changeset ERP-IAM:20261121210000-3
INSERT INTO language (code, name, country_code, enabled, system_lang, created_by, created_date)
VALUES ('vi', 'Tiếng Việt', 'VN', 1, 1, 'system', UTC_TIMESTAMP(6)),
       ('en', 'English', 'GB', 1, 1, 'system', UTC_TIMESTAMP(6)),
       ('ko', '한국어', 'KR', 1, 1, 'system', UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE code = code;
