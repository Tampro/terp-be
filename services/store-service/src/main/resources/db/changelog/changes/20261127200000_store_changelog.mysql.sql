-- liquibase formatted sql

-- changeset ERP-STORE:20261127200000-1
-- A store of a chain: sells from one warehouse of inventory-service (no FK: another database), takes orders in one
-- daily window [order_start, order_end) of its own time zone, with its delivery fees. Soft delete: deleted_key is 0
-- while live and the row id once deleted, so a deleted store's code and warehouse can be used again.
CREATE TABLE store
(
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT         NOT NULL,
    code                    VARCHAR(20)    NOT NULL,
    name                    VARCHAR(255)   NOT NULL,
    warehouse_id            BIGINT         NOT NULL,
    address                 VARCHAR(500)   NULL,
    lat                     DECIMAL(9, 6)  NULL,
    lon                     DECIMAL(9, 6)  NULL,
    phone                   VARCHAR(30)    NULL,
    time_zone               VARCHAR(40)    NOT NULL DEFAULT 'Asia/Seoul',
    order_start             TIME           NOT NULL,
    order_end               TIME           NOT NULL,
    delivery_fee            DECIMAL(19, 4) NOT NULL DEFAULT 0,
    free_delivery_threshold DECIMAL(19, 4) NOT NULL DEFAULT 0,
    min_order_amount        DECIMAL(19, 4) NOT NULL DEFAULT 0,
    first_order_min_amount  DECIMAL(19, 4) NOT NULL DEFAULT 0,
    bag_fee                 DECIMAL(19, 4) NOT NULL DEFAULT 0,
    active                  BIT(1)         NOT NULL DEFAULT 1,
    deleted                 BIT(1)         NOT NULL DEFAULT 0,
    deleted_key             BIGINT         NOT NULL DEFAULT 0,
    created_by              VARCHAR(64)    NULL,
    created_date            DATETIME(6)    NULL,
    updated_by              VARCHAR(64)    NULL,
    updated_date            DATETIME(6)    NULL,
    UNIQUE KEY uk_store_code (company_id, code, deleted_key),
    UNIQUE KEY uk_store_warehouse (company_id, warehouse_id, deleted_key),
    INDEX idx_store_company (company_id, deleted, code)
);
