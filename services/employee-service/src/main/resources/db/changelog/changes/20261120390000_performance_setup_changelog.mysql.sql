-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120390000-1
-- Performance setup (docs/analysis/06 7.3.5, 7.3.6): goal types and indicator categories, company-managed; names
-- unique per company and kind (case-insensitive).
CREATE TABLE performance_catalog
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
    INDEX idx_performance_catalog_company (company_id, kind)
);

-- changeset ERP-EMPLOYEE:20261120390000-2
-- Indicators (7.3.4). target_value is free text ("95%", "4/5"); weight feeds the weighted review rating
-- (PO, 2026-11-20).
CREATE TABLE performance_indicator
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT        NOT NULL,
    category_id      BIGINT        NOT NULL,
    name             VARCHAR(150)  NOT NULL,
    description      VARCHAR(1000) NULL,
    measurement_unit VARCHAR(50)   NULL,
    target_value     VARCHAR(100)  NULL,
    weight           DECIMAL(6, 2) NOT NULL DEFAULT 1,
    status           VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    deleted          BIT(1)        NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_date     DATETIME(6)   NULL,
    updated_by       VARCHAR(64)   NULL,
    updated_date     DATETIME(6)   NULL,
    INDEX idx_performance_indicator_company (company_id, category_id),
    CONSTRAINT fk_performance_indicator_category FOREIGN KEY (category_id) REFERENCES performance_catalog (id)
);

-- changeset ERP-EMPLOYEE:20261120390000-3
-- Review cycles (7.3.3).
CREATE TABLE review_cycle
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(150) NOT NULL,
    frequency    VARCHAR(12)  NOT NULL,
    description  VARCHAR(500) NULL,
    status       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_review_cycle_company (company_id)
);
