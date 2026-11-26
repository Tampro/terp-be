-- liquibase formatted sql

-- changeset ERP-INVENTORY:20261127100000-1
-- Sale price and on-sale status of an item at one store (a store is a warehouse). An item without a live row is not
-- sold at that store. Soft delete: deleted_key is 0 while live and the row id once deleted, so the unique key lets a
-- deleted (warehouse, item) pair be created again (MySQL has no partial unique index).
CREATE TABLE store_item
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id         BIGINT         NOT NULL,
    warehouse_id       BIGINT         NOT NULL,
    item_id            BIGINT         NOT NULL,
    sale_price         DECIMAL(19, 4) NOT NULL,
    status             VARCHAR(20)    NOT NULL DEFAULT 'ON_SALE',
    purchase_limit_qty INT            NULL,
    deleted            BIT(1)         NOT NULL DEFAULT 0,
    deleted_key        BIGINT         NOT NULL DEFAULT 0,
    created_by         VARCHAR(64)    NULL,
    created_date       DATETIME(6)    NULL,
    updated_by         VARCHAR(64)    NULL,
    updated_date       DATETIME(6)    NULL,
    UNIQUE KEY uk_store_item (company_id, warehouse_id, item_id, deleted_key),
    INDEX idx_store_item_list (company_id, warehouse_id, status, item_id),
    CONSTRAINT fk_store_item_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_store_item_item FOREIGN KEY (item_id) REFERENCES item (id)
);
