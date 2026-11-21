-- liquibase formatted sql

-- changeset ERP-ASSET:20261120500000-1
-- Asset types (docs/analysis/06 8.2.4): the categories of the company; name unique per company (checked by the
-- service, soft-deleted rows excluded).
CREATE TABLE asset_type
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT        NOT NULL,
    name         VARCHAR(150)  NOT NULL,
    description  VARCHAR(1000) NULL,
    deleted      BIT(1)        NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_date DATETIME(6)   NULL,
    updated_by   VARCHAR(64)   NULL,
    updated_date DATETIME(6)   NULL,
    INDEX idx_asset_type_company (company_id)
);

-- changeset ERP-ASSET:20261120500000-2
-- Assets (docs/analysis/06 8.2.2). Image and documents are files of the media library (media-service); asset code
-- unique per company (checked by the service). Useful life in years, as the Add Asset form asks.
CREATE TABLE asset
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT         NOT NULL,
    asset_type_id        BIGINT         NOT NULL,
    name                 VARCHAR(200)   NOT NULL,
    asset_code           VARCHAR(50)    NOT NULL,
    serial_number        VARCHAR(100)   NULL,
    purchase_date        DATE           NULL,
    purchase_cost        DECIMAL(15, 2) NOT NULL DEFAULT 0,
    status               VARCHAR(12)    NOT NULL DEFAULT 'AVAILABLE',
    asset_condition      VARCHAR(10)    NOT NULL DEFAULT 'GOOD',
    description          VARCHAR(2000)  NULL,
    location             VARCHAR(200)   NULL,
    supplier             VARCHAR(200)   NULL,
    warranty_info        VARCHAR(1000)  NULL,
    warranty_expiry_date DATE           NULL,
    image_file_id        BIGINT         NULL,
    depreciation_method  VARCHAR(20)    NOT NULL DEFAULT 'STRAIGHT_LINE',
    useful_life_years    INT            NOT NULL DEFAULT 0,
    salvage_value        DECIMAL(15, 2) NOT NULL DEFAULT 0,
    deleted              BIT(1)         NOT NULL DEFAULT 0,
    created_by           VARCHAR(64)    NULL,
    created_date         DATETIME(6)    NULL,
    updated_by           VARCHAR(64)    NULL,
    updated_date         DATETIME(6)    NULL,
    INDEX idx_asset_company (company_id),
    INDEX idx_asset_company_code (company_id, asset_code),
    CONSTRAINT fk_asset_type FOREIGN KEY (asset_type_id) REFERENCES asset_type (id)
);

-- changeset ERP-ASSET:20261120500000-3
-- Attached documents (invoices, manuals, warranty): media library files.
CREATE TABLE asset_document
(
    asset_id BIGINT NOT NULL,
    file_id  BIGINT NOT NULL,
    PRIMARY KEY (asset_id, file_id),
    CONSTRAINT fk_asset_document_asset FOREIGN KEY (asset_id) REFERENCES asset (id)
);
