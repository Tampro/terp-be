-- liquibase formatted sql

-- changeset ERP-INVENTORY:20261121400000-1
-- Product/service categories (ERP manual 9 / 8.3; TERP Core product_service_categories). Name unique per company
-- among rows not deleted (checked by the service: a unique key would also block re-using a deleted name).
CREATE TABLE product_category
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    color        VARCHAR(7)   NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_product_category_company (company_id)
);

-- changeset ERP-INVENTORY:20261121400000-2
CREATE TABLE product_tax
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    name         VARCHAR(100)   NOT NULL,
    rate         DECIMAL(19, 4) NOT NULL DEFAULT 0,
    deleted      BIT(1)         NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_by   VARCHAR(64)    NULL,
    updated_date DATETIME(6)    NULL,
    INDEX idx_product_tax_company (company_id)
);

-- changeset ERP-INVENTORY:20261121400000-3
CREATE TABLE product_unit
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT      NOT NULL,
    name         VARCHAR(50) NOT NULL,
    deleted      BIT(1)      NOT NULL DEFAULT 0,
    created_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL,
    INDEX idx_product_unit_company (company_id)
);

-- changeset ERP-INVENTORY:20261121400000-4
-- Items (TERP Core product_service_items). SKU unique per company among rows not deleted (checked by the service).
CREATE TABLE item
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT         NOT NULL,
    type              VARCHAR(10)    NOT NULL,
    name              VARCHAR(255)   NOT NULL,
    sku               VARCHAR(100)   NOT NULL,
    tax_id            BIGINT         NULL,
    category_id       BIGINT         NOT NULL,
    unit_id           BIGINT         NOT NULL,
    sale_price        DECIMAL(19, 4) NOT NULL DEFAULT 0,
    purchase_price    DECIMAL(19, 4) NOT NULL DEFAULT 0,
    short_description VARCHAR(500)   NULL,
    description       TEXT           NULL,
    deleted           BIT(1)         NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)    NULL,
    created_date      DATETIME(6)    NULL,
    updated_by        VARCHAR(64)    NULL,
    updated_date      DATETIME(6)    NULL,
    INDEX idx_item_company (company_id),
    INDEX idx_item_company_sku (company_id, sku),
    CONSTRAINT fk_item_tax FOREIGN KEY (tax_id) REFERENCES product_tax (id),
    CONSTRAINT fk_item_category FOREIGN KEY (category_id) REFERENCES product_category (id),
    CONSTRAINT fk_item_unit FOREIGN KEY (unit_id) REFERENCES product_unit (id)
);

-- changeset ERP-INVENTORY:20261121400000-5
CREATE TABLE warehouse
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(255) NOT NULL,
    address      VARCHAR(500) NOT NULL,
    city         VARCHAR(100) NOT NULL,
    zip_code     VARCHAR(20)  NOT NULL,
    phone        VARCHAR(30)  NULL,
    email        VARCHAR(255) NULL,
    active       BIT(1)       NOT NULL DEFAULT 1,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_warehouse_company (company_id)
);

-- changeset ERP-INVENTORY:20261121400000-6
-- Current quantity of an item in a warehouse; rows are locked (SELECT ... FOR UPDATE) while stock changes.
-- No soft delete: a row is the running total of the movements.
CREATE TABLE warehouse_stock
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    item_id      BIGINT         NOT NULL,
    warehouse_id BIGINT         NOT NULL,
    quantity     DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_by   VARCHAR(64)    NULL,
    updated_date DATETIME(6)    NULL,
    UNIQUE KEY uk_warehouse_stock (company_id, item_id, warehouse_id),
    INDEX idx_warehouse_stock_warehouse (warehouse_id),
    CONSTRAINT fk_stock_item FOREIGN KEY (item_id) REFERENCES item (id),
    CONSTRAINT fk_stock_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
);

-- changeset ERP-INVENTORY:20261121400000-7
-- Immutable ledger of every stock change. (company_id, source_type, source_id) makes /stock/moves idempotent.
CREATE TABLE stock_movement
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    item_id      BIGINT         NOT NULL,
    warehouse_id BIGINT         NOT NULL,
    quantity     DECIMAL(19, 4) NOT NULL,
    source_type  VARCHAR(30)    NOT NULL,
    source_id    BIGINT         NULL,
    created_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    INDEX idx_stock_movement_source (company_id, source_type, source_id),
    INDEX idx_stock_movement_item (item_id),
    CONSTRAINT fk_movement_item FOREIGN KEY (item_id) REFERENCES item (id),
    CONSTRAINT fk_movement_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
);
