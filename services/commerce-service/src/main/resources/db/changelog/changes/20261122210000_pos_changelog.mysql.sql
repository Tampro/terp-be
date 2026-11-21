-- liquibase formatted sql

-- changeset ERP-COMMERCE:20261122210000-1
-- Paid POS sales (ERP manual 10); written only after stock left the warehouse and the income reached the bank account.
CREATE TABLE pos_order
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT         NOT NULL,
    pos_number        VARCHAR(30)    NOT NULL,
    receipt_number    VARCHAR(30)    NOT NULL,
    order_date        DATE           NOT NULL,
    customer_id       BIGINT         NULL,
    customer_name     VARCHAR(255)   NOT NULL,
    warehouse_id      BIGINT         NOT NULL,
    warehouse_name    VARCHAR(255)   NOT NULL,
    bank_account_id   BIGINT         NOT NULL,
    bank_account_name VARCHAR(255)   NOT NULL,
    subtotal          DECIMAL(19, 4) NOT NULL DEFAULT 0,
    tax_total         DECIMAL(19, 4) NOT NULL DEFAULT 0,
    discount          DECIMAL(19, 4) NOT NULL DEFAULT 0,
    total             DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)    NULL,
    updated_by        VARCHAR(64)    NULL,
    created_date      DATETIME(6)    NULL,
    updated_date      DATETIME(6)    NULL,
    CONSTRAINT uk_pos_order_number UNIQUE (company_id, pos_number),
    CONSTRAINT uk_pos_order_receipt UNIQUE (company_id, receipt_number),
    INDEX idx_pos_order_list (company_id, order_date),
    INDEX idx_pos_order_customer (company_id, customer_id),
    INDEX idx_pos_order_warehouse (company_id, warehouse_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-COMMERCE:20261122210000-2
CREATE TABLE pos_order_line
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT         NOT NULL,
    position   INT            NOT NULL,
    item_id    BIGINT         NOT NULL,
    item_type  VARCHAR(10)    NOT NULL,
    item_name  VARCHAR(255)   NOT NULL,
    sku        VARCHAR(100)   NULL,
    quantity   DECIMAL(19, 4) NOT NULL,
    unit_price DECIMAL(19, 4) NOT NULL,
    tax_name   VARCHAR(100)   NULL,
    tax_rate   DECIMAL(9, 4)  NOT NULL DEFAULT 0,
    amount     DECIMAL(19, 4) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(19, 4) NOT NULL DEFAULT 0,
    INDEX idx_pos_order_line_order (order_id),
    INDEX idx_pos_order_line_item (item_id),
    CONSTRAINT fk_pos_order_line_order FOREIGN KEY (order_id) REFERENCES pos_order (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
