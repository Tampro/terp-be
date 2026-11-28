-- liquibase formatted sql

-- changeset ERP-ORDER:20261129190000-1
-- Mileage policy of a company (express-mileage spec); no row = the defaults (on, 1 %, 12 months, 1,000 minimum).
CREATE TABLE mileage_policy
(
    company_id    BIGINT        NOT NULL PRIMARY KEY,
    active        BIT           NOT NULL,
    earn_rate     DECIMAL(5, 2) NOT NULL,
    expiry_months INT           NOT NULL,
    min_redeem    INT           NOT NULL,
    created_by    VARCHAR(64)   NULL,
    updated_by    VARCHAR(64)   NULL,
    created_date  DATETIME(6)   NULL,
    updated_date  DATETIME(6)   NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ORDER:20261129190000-2
-- A lot of points (earned on a delivered order or added by Back Office), spent FIFO by expiry, expired per lot.
-- earn_order_id is unique: an order earns once. The balance is the sum of remaining of unexpired lots.
CREATE TABLE mileage_lot
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT      NOT NULL,
    customer_id   BIGINT      NOT NULL,
    source        VARCHAR(10) NOT NULL,
    points        INT         NOT NULL,
    remaining     INT         NOT NULL,
    expires_at    DATETIME(6) NOT NULL,
    earn_order_id BIGINT      NULL,
    created_date  DATETIME(6) NOT NULL,
    UNIQUE KEY uk_mileage_lot_earn_order (earn_order_id),
    INDEX idx_mileage_lot_customer (company_id, customer_id, expires_at),
    INDEX idx_mileage_lot_due (expires_at, remaining)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ORDER:20261129190000-3
-- The customer's statement: one signed row per earn, use, release, expiry and adjustment.
CREATE TABLE mileage_entry
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    customer_id  BIGINT       NOT NULL,
    type         VARCHAR(10)  NOT NULL,
    points       INT          NOT NULL,
    order_id     BIGINT       NULL,
    order_no     VARCHAR(30)  NULL,
    lot_id       BIGINT       NULL,
    reason       VARCHAR(200) NULL,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NOT NULL,
    INDEX idx_mileage_entry_customer (company_id, customer_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ORDER:20261129190000-4
-- What an order's use took from each lot, so a cancel gives exactly those points back.
CREATE TABLE mileage_use_lot
(
    order_id BIGINT NOT NULL,
    lot_id   BIGINT NOT NULL,
    points   INT    NOT NULL,
    PRIMARY KEY (order_id, lot_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ORDER:20261129190000-5
ALTER TABLE customer_order
    ADD COLUMN points_used INT NOT NULL DEFAULT 0;
