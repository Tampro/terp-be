-- liquibase formatted sql

-- changeset ERP-COMMERCE:20261121620000-1
-- Purchase invoices from vendors (ERP manual 8.1): DRAFT -> POSTED -> PARTIAL -> PAID; posting adds stock.
CREATE TABLE purchase_invoice
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT         NOT NULL,
    invoice_number VARCHAR(30)    NOT NULL,
    vendor_id      BIGINT         NOT NULL,
    vendor_name    VARCHAR(255)   NOT NULL,
    warehouse_id   BIGINT         NULL,
    invoice_date   DATE           NOT NULL,
    due_date       DATE           NOT NULL,
    payment_terms  VARCHAR(100)   NULL,
    notes          TEXT           NULL,
    status         VARCHAR(12)    NOT NULL,
    subtotal       DECIMAL(19, 4) NOT NULL DEFAULT 0,
    discount_total DECIMAL(19, 4) NOT NULL DEFAULT 0,
    tax_total      DECIMAL(19, 4) NOT NULL DEFAULT 0,
    total          DECIMAL(19, 4) NOT NULL DEFAULT 0,
    paid_amount    DECIMAL(19, 4) NOT NULL DEFAULT 0,
    balance        DECIMAL(19, 4) NOT NULL DEFAULT 0,
    posted_at      DATETIME(6)    NULL,
    deleted        BIT(1)         NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)    NULL,
    updated_by     VARCHAR(64)    NULL,
    created_date   DATETIME(6)    NULL,
    updated_date   DATETIME(6)    NULL,
    CONSTRAINT uk_purchase_invoice_number UNIQUE (company_id, invoice_number),
    INDEX idx_purchase_invoice_list (company_id, status, invoice_date),
    INDEX idx_purchase_invoice_vendor (company_id, vendor_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-COMMERCE:20261121620000-2
CREATE TABLE purchase_invoice_line
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id   BIGINT         NOT NULL,
    position     INT            NOT NULL,
    item_id      BIGINT         NOT NULL,
    item_type    VARCHAR(10)    NOT NULL,
    item_name    VARCHAR(255)   NOT NULL,
    sku          VARCHAR(100)   NULL,
    quantity     DECIMAL(19, 4) NOT NULL,
    unit_price   DECIMAL(19, 4) NOT NULL,
    discount_pct DECIMAL(9, 4)  NOT NULL DEFAULT 0,
    tax_name     VARCHAR(100)   NULL,
    tax_rate     DECIMAL(9, 4)  NOT NULL DEFAULT 0,
    discount     DECIMAL(19, 4) NOT NULL DEFAULT 0,
    tax_amount   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    line_total   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    INDEX idx_purchase_invoice_line_invoice (invoice_id),
    CONSTRAINT fk_purchase_invoice_line_invoice FOREIGN KEY (invoice_id) REFERENCES purchase_invoice (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-COMMERCE:20261121620000-3
-- Purchase returns of a posted purchase invoice (ERP manual 8.2): DRAFT -> APPROVED -> COMPLETED (stock leaves).
CREATE TABLE purchase_return
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT         NOT NULL,
    return_number  VARCHAR(30)    NOT NULL,
    invoice_id     BIGINT         NOT NULL,
    vendor_id      BIGINT         NOT NULL,
    vendor_name    VARCHAR(255)   NOT NULL,
    warehouse_id   BIGINT         NULL,
    return_date    DATE           NOT NULL,
    reason         VARCHAR(255)   NULL,
    notes          TEXT           NULL,
    status         VARCHAR(12)    NOT NULL,
    subtotal       DECIMAL(19, 4) NOT NULL DEFAULT 0,
    discount_total DECIMAL(19, 4) NOT NULL DEFAULT 0,
    tax_total      DECIMAL(19, 4) NOT NULL DEFAULT 0,
    total          DECIMAL(19, 4) NOT NULL DEFAULT 0,
    completed_at   DATETIME(6)    NULL,
    deleted        BIT(1)         NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)    NULL,
    updated_by     VARCHAR(64)    NULL,
    created_date   DATETIME(6)    NULL,
    updated_date   DATETIME(6)    NULL,
    CONSTRAINT uk_purchase_return_number UNIQUE (company_id, return_number),
    INDEX idx_purchase_return_list (company_id, status, return_date),
    INDEX idx_purchase_return_invoice (invoice_id),
    CONSTRAINT fk_purchase_return_invoice FOREIGN KEY (invoice_id) REFERENCES purchase_invoice (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-COMMERCE:20261121620000-4
CREATE TABLE purchase_return_line
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_id       BIGINT         NOT NULL,
    position        INT            NOT NULL,
    invoice_line_id BIGINT         NOT NULL,
    item_id         BIGINT         NOT NULL,
    item_type       VARCHAR(10)    NOT NULL,
    item_name       VARCHAR(255)   NOT NULL,
    sku             VARCHAR(100)   NULL,
    quantity        DECIMAL(19, 4) NOT NULL,
    unit_price      DECIMAL(19, 4) NOT NULL,
    discount_pct    DECIMAL(9, 4)  NOT NULL DEFAULT 0,
    tax_rate        DECIMAL(9, 4)  NOT NULL DEFAULT 0,
    discount        DECIMAL(19, 4) NOT NULL DEFAULT 0,
    tax_amount      DECIMAL(19, 4) NOT NULL DEFAULT 0,
    line_total      DECIMAL(19, 4) NOT NULL DEFAULT 0,
    reason          VARCHAR(255)   NULL,
    INDEX idx_purchase_return_line_return (return_id),
    INDEX idx_purchase_return_line_invoice_line (invoice_line_id),
    CONSTRAINT fk_purchase_return_line_return FOREIGN KEY (return_id) REFERENCES purchase_return (id),
    CONSTRAINT fk_purchase_return_line_invoice_line FOREIGN KEY (invoice_line_id) REFERENCES purchase_invoice_line (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
