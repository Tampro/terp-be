-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120180000-1
-- Award types (docs/analysis/06 §6.3.6), one list per company. icon is a key of the FE icon set.
CREATE TABLE award_type
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(150) NOT NULL,
    description  VARCHAR(500) NULL,
    icon         VARCHAR(50)  NULL,
    status       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_award_type_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
