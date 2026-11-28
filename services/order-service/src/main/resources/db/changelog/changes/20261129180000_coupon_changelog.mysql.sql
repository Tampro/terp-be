-- liquibase formatted sql

-- changeset ERP-ORDER:20261129180000-1
-- Company coupons for Express orders (express-coupon spec). Soft delete: deleted_key is 0 while live and the row id
-- once deleted, so a deleted code can be created again. used_count is reserved under a row lock when an order is placed.
CREATE TABLE coupon
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id         BIGINT         NOT NULL,
    code               VARCHAR(30)    NOT NULL,
    name               VARCHAR(100)   NOT NULL,
    type               VARCHAR(10)    NOT NULL,
    value              DECIMAL(19, 4) NOT NULL,
    max_discount       DECIMAL(19, 4) NULL,
    min_subtotal       DECIMAL(19, 4) NULL,
    starts_at          DATETIME(6)    NOT NULL,
    ends_at            DATETIME(6)    NOT NULL,
    total_limit        INT            NULL,
    per_customer_limit INT            NULL,
    used_count         INT            NOT NULL DEFAULT 0,
    active             BIT            NOT NULL,
    deleted            BIT            NOT NULL DEFAULT 0,
    deleted_key        BIGINT         NOT NULL DEFAULT 0,
    created_by         VARCHAR(64)    NULL,
    updated_by         VARCHAR(64)    NULL,
    created_date       DATETIME(6)    NULL,
    updated_date       DATETIME(6)    NULL,
    UNIQUE KEY uk_coupon_code (company_id, code, deleted_key),
    INDEX idx_coupon_company (company_id, deleted, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ORDER:20261129180000-2
-- One row per order that holds a use (deleted when the order is cancelled or expires).
CREATE TABLE coupon_use
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id    BIGINT      NOT NULL,
    customer_id  BIGINT      NOT NULL,
    order_id     BIGINT      NOT NULL,
    created_date DATETIME(6) NOT NULL,
    UNIQUE KEY uk_coupon_use_order (order_id),
    INDEX idx_coupon_use_customer (coupon_id, customer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ORDER:20261129180000-3
-- The coupon applied to an order (snapshot): discount off the goods subtotal.
ALTER TABLE customer_order
    ADD COLUMN coupon_id   BIGINT         NULL,
    ADD COLUMN coupon_code VARCHAR(30)    NULL,
    ADD COLUMN discount    DECIMAL(19, 4) NOT NULL DEFAULT 0;
