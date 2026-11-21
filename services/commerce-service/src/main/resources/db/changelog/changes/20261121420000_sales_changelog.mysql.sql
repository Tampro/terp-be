-- liquibase formatted sql

-- changeset ERP-COMMERCE:20261121420000-1
-- Numbers per company, prefix (QT, SP, SI, SR) and month: QT-2026-11-001. Locked row by row, never reused.
CREATE TABLE document_sequence
(
    company_id BIGINT      NOT NULL,
    prefix     VARCHAR(4)  NOT NULL,
    period     VARCHAR(7)  NOT NULL,
    next_value INT         NOT NULL,
    PRIMARY KEY (company_id, prefix, period)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-COMMERCE:20261121420000-2
-- Quotations, proposals and sales invoices share one shape (ERP manual 6.1, 6.2, 6.4); kind tells them apart.
-- customer_id and warehouse_id are ids of accounting- and inventory-service (no cross-database key).
CREATE TABLE sales_document
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT         NOT NULL,
    kind                 VARCHAR(12)    NOT NULL,
    doc_number           VARCHAR(30)    NOT NULL,
    invoice_type         VARCHAR(10)    NULL,
    customer_id          BIGINT         NOT NULL,
    customer_name        VARCHAR(255)   NOT NULL,
    warehouse_id         BIGINT         NULL,
    doc_date             DATE           NOT NULL,
    due_date             DATE           NOT NULL,
    payment_terms        VARCHAR(100)   NULL,
    notes                TEXT           NULL,
    status               VARCHAR(12)    NOT NULL,
    subtotal             DECIMAL(19, 4) NOT NULL DEFAULT 0,
    discount_total       DECIMAL(19, 4) NOT NULL DEFAULT 0,
    tax_total            DECIMAL(19, 4) NOT NULL DEFAULT 0,
    total                DECIMAL(19, 4) NOT NULL DEFAULT 0,
    paid_amount          DECIMAL(19, 4) NOT NULL DEFAULT 0,
    balance              DECIMAL(19, 4) NOT NULL DEFAULT 0,
    version_of_id        BIGINT         NULL,
    version_no           INT            NOT NULL DEFAULT 1,
    converted_invoice_id BIGINT         NULL,
    converted_from_id    BIGINT         NULL,
    posted_at            DATETIME(6)    NULL,
    deleted              BIT(1)         NOT NULL DEFAULT 0,
    created_by           VARCHAR(64)    NULL,
    updated_by           VARCHAR(64)    NULL,
    created_date         DATETIME(6)    NULL,
    updated_date         DATETIME(6)    NULL,
    CONSTRAINT uk_sales_document_number UNIQUE (company_id, doc_number),
    INDEX idx_sales_document_list (company_id, kind, status, doc_date),
    INDEX idx_sales_document_customer (company_id, customer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-COMMERCE:20261121420000-3
-- Item name, sku, type and tax are copied when the line is saved: later item edits don't change a sent document.
CREATE TABLE sales_document_line
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id  BIGINT         NOT NULL,
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
    INDEX idx_sales_document_line_doc (document_id),
    CONSTRAINT fk_sales_document_line_doc FOREIGN KEY (document_id) REFERENCES sales_document (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-COMMERCE:20261121420000-4
-- Sales returns of a posted invoice (ERP manual 6.3): DRAFT -> APPROVED -> COMPLETED (stock comes back).
CREATE TABLE sales_return
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT         NOT NULL,
    return_number  VARCHAR(30)    NOT NULL,
    invoice_id     BIGINT         NOT NULL,
    customer_id    BIGINT         NOT NULL,
    customer_name  VARCHAR(255)   NOT NULL,
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
    CONSTRAINT uk_sales_return_number UNIQUE (company_id, return_number),
    INDEX idx_sales_return_list (company_id, status, return_date),
    INDEX idx_sales_return_invoice (invoice_id),
    CONSTRAINT fk_sales_return_invoice FOREIGN KEY (invoice_id) REFERENCES sales_document (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-COMMERCE:20261121420000-5
CREATE TABLE sales_return_line
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
    INDEX idx_sales_return_line_return (return_id),
    INDEX idx_sales_return_line_invoice_line (invoice_line_id),
    CONSTRAINT fk_sales_return_line_return FOREIGN KEY (return_id) REFERENCES sales_return (id),
    CONSTRAINT fk_sales_return_line_invoice_line FOREIGN KEY (invoice_line_id) REFERENCES sales_document_line (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
