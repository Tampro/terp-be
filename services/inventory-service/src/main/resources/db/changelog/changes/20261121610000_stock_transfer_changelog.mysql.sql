-- liquibase formatted sql

-- changeset ERP-INVENTORY:20261121610000-1
-- Stock moved between two warehouses of a company (ERP manual 8.4). No status: creating it moves the stock
-- (stock_movement source_type TRANSFER), deleting it moves it back (TRANSFER_DELETE).
CREATE TABLE stock_transfer
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT         NOT NULL,
    from_warehouse_id BIGINT         NOT NULL,
    to_warehouse_id   BIGINT         NOT NULL,
    item_id           BIGINT         NOT NULL,
    quantity          DECIMAL(19, 4) NOT NULL,
    transfer_date     DATE           NOT NULL,
    deleted           BIT(1)         NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)    NULL,
    created_date      DATETIME(6)    NULL,
    updated_by        VARCHAR(64)    NULL,
    updated_date      DATETIME(6)    NULL,
    INDEX idx_stock_transfer_company_date (company_id, transfer_date),
    CONSTRAINT fk_transfer_from_warehouse FOREIGN KEY (from_warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_transfer_to_warehouse FOREIGN KEY (to_warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_transfer_item FOREIGN KEY (item_id) REFERENCES item (id)
);
