-- liquibase formatted sql

-- changeset ERP-ORDER:20261128210000-1
-- A customer's order of one store's cart: everything priced is a snapshot taken at checkout, never re-priced.
-- Personal data: recipient, phone (AES-256-GCM, phone_enc), address — needed to deliver.
CREATE TABLE customer_order
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT         NOT NULL,
    customer_id    BIGINT         NOT NULL,
    store_id       BIGINT         NOT NULL,
    warehouse_id   BIGINT         NOT NULL,
    order_no       VARCHAR(20)    NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    failure_reason VARCHAR(20)    NULL,
    substitution   VARCHAR(5)     NOT NULL,
    subtotal       DECIMAL(19, 4) NOT NULL,
    delivery_fee   DECIMAL(19, 4) NOT NULL,
    bag_fee        DECIMAL(19, 4) NOT NULL,
    total          DECIMAL(19, 4) NOT NULL,
    store_name     VARCHAR(255)   NOT NULL,
    recipient_name VARCHAR(50)    NOT NULL,
    phone_enc      VARCHAR(255)   NOT NULL,
    postcode       VARCHAR(5)     NOT NULL,
    address1       VARCHAR(200)   NOT NULL,
    address2       VARCHAR(200)   NULL,
    lat            DECIMAL(9, 6)  NOT NULL,
    lon            DECIMAL(9, 6)  NOT NULL,
    delivery_note  VARCHAR(200)   NULL,
    note           VARCHAR(200)   NULL,
    paid_at        DATETIME(6)    NULL,
    cancelled_at   DATETIME(6)    NULL,
    created_by     VARCHAR(64)    NULL,
    created_date   DATETIME(6)    NULL,
    updated_by     VARCHAR(64)    NULL,
    updated_date   DATETIME(6)    NULL,
    UNIQUE KEY uk_order_number (company_id, order_no),
    INDEX idx_order_store (company_id, store_id, status, created_date),
    INDEX idx_order_customer (customer_id, created_date),
    INDEX idx_order_pending (status, created_date)
);

-- changeset ERP-ORDER:20261128210000-2
CREATE TABLE customer_order_line
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT         NOT NULL,
    item_id      BIGINT         NOT NULL,
    name         VARCHAR(255)   NOT NULL,
    sku          VARCHAR(100)   NULL,
    unit_price   DECIMAL(19, 4) NOT NULL,
    quantity     INT            NOT NULL,
    line_total   DECIMAL(19, 4) NOT NULL,
    created_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_by   VARCHAR(64)    NULL,
    updated_date DATETIME(6)    NULL,
    INDEX idx_order_line_order (order_id),
    CONSTRAINT fk_order_line_order FOREIGN KEY (order_id) REFERENCES customer_order (id) ON DELETE CASCADE
);

-- changeset ERP-ORDER:20261128210000-3
-- Order numbers per company per day (HE-yyyyMMdd-NNNNNN), taken under a row lock.
CREATE TABLE order_sequence
(
    company_id BIGINT NOT NULL,
    day        DATE   NOT NULL,
    last_no    INT    NOT NULL,
    PRIMARY KEY (company_id, day)
);
