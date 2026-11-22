-- liquibase formatted sql

-- changeset ERP-COMMERCE:20261122420000-1
-- A cleared accounting payment applied to a sales invoice. Unique (invoice, payment): accounting retries a clear
-- after a failure and the amount must be taken off the balance once only.
CREATE TABLE sales_invoice_payment
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    invoice_id   BIGINT         NOT NULL,
    payment_id   BIGINT         NOT NULL,
    amount       DECIMAL(19, 4) NOT NULL,
    created_by   VARCHAR(64)    NULL,
    updated_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_date DATETIME(6)    NULL,
    CONSTRAINT uk_sales_invoice_payment UNIQUE (invoice_id, payment_id),
    INDEX idx_sales_invoice_payment_payment (company_id, payment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-COMMERCE:20261122420000-2
CREATE TABLE purchase_invoice_payment
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    invoice_id   BIGINT         NOT NULL,
    payment_id   BIGINT         NOT NULL,
    amount       DECIMAL(19, 4) NOT NULL,
    created_by   VARCHAR(64)    NULL,
    updated_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_date DATETIME(6)    NULL,
    CONSTRAINT uk_purchase_invoice_payment UNIQUE (invoice_id, payment_id),
    INDEX idx_purchase_invoice_payment_payment (company_id, payment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
