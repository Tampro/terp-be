-- liquibase formatted sql

-- changeset ERP-ORDER:20261130100000-1
-- A customer's return of delivered items (order-return spec): REQUESTED -> APPROVED / REJECTED -> COMPLETED.
-- Amounts are computed once at request (approve may switch the reason and so the fee) and kept for the refund.
CREATE TABLE order_return
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT         NOT NULL,
    order_id      BIGINT         NOT NULL,
    order_no      VARCHAR(30)    NOT NULL,
    store_id      BIGINT         NOT NULL,
    customer_id   BIGINT         NOT NULL,
    return_no     VARCHAR(30)    NOT NULL,
    status        VARCHAR(16)    NOT NULL,
    reason        VARCHAR(20)    NOT NULL,
    note          VARCHAR(500)   NULL,
    reject_note   VARCHAR(200)   NULL,
    goods_amount  DECIMAL(19, 4) NOT NULL,
    refund_share  DECIMAL(19, 4) NOT NULL,
    pickup_fee    DECIMAL(19, 4) NOT NULL,
    refund_amount DECIMAL(19, 4) NOT NULL,
    cost_amount   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    points_back   INT            NOT NULL DEFAULT 0,
    earned_back   INT            NOT NULL DEFAULT 0,
    approved_at   DATETIME(6)    NULL,
    rejected_at   DATETIME(6)    NULL,
    completed_at  DATETIME(6)    NULL,
    created_by    VARCHAR(64)    NULL,
    updated_by    VARCHAR(64)    NULL,
    created_date  DATETIME(6)    NULL,
    updated_date  DATETIME(6)    NULL,
    UNIQUE KEY uk_order_return_no (company_id, return_no),
    INDEX idx_order_return_status (company_id, status, id),
    INDEX idx_order_return_order (order_id),
    CONSTRAINT fk_order_return_order FOREIGN KEY (order_id) REFERENCES customer_order (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ORDER:20261130100000-2
CREATE TABLE order_return_line
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_id     BIGINT         NOT NULL,
    order_line_id BIGINT         NOT NULL,
    item_id       BIGINT         NOT NULL,
    name          VARCHAR(255)   NOT NULL,
    quantity      INT            NOT NULL,
    unit_price    DECIMAL(19, 4) NOT NULL,
    share         DECIMAL(19, 4) NOT NULL,
    unit_cost     DECIMAL(19, 4) NOT NULL,
    created_by    VARCHAR(64)    NULL,
    updated_by    VARCHAR(64)    NULL,
    created_date  DATETIME(6)    NULL,
    updated_date  DATETIME(6)    NULL,
    INDEX idx_order_return_line_return (return_id),
    CONSTRAINT fk_order_return_line_return FOREIGN KEY (return_id) REFERENCES order_return (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ORDER:20261130100000-3
-- Return policy of a company; no row = the default pick-up fee 3,000 won for a change of mind.
CREATE TABLE return_policy
(
    company_id   BIGINT         NOT NULL PRIMARY KEY,
    pickup_fee   DECIMAL(19, 4) NOT NULL,
    created_by   VARCHAR(64)    NULL,
    updated_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_date DATETIME(6)    NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ORDER:20261130100000-4
-- RQ-yyyyMMdd-nnnnnn per company and day, apart from order_sequence so order numbers never skip.
CREATE TABLE return_sequence
(
    company_id BIGINT NOT NULL,
    day        DATE   NOT NULL,
    last_no    INT    NOT NULL,
    PRIMARY KEY (company_id, day)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
