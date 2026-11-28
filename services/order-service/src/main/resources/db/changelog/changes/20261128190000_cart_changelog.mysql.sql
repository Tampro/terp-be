-- liquibase formatted sql

-- changeset ERP-ORDER:20261128190000-1
-- One cart per (membership, store). No prices: a cart is always priced live from inventory.
CREATE TABLE cart
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT      NOT NULL,
    customer_id   BIGINT      NOT NULL,
    store_id      BIGINT      NOT NULL,
    bag_requested BIT(1)      NOT NULL DEFAULT 0,
    created_by    VARCHAR(64) NULL,
    created_date  DATETIME(6) NULL,
    updated_by    VARCHAR(64) NULL,
    updated_date  DATETIME(6) NULL,
    UNIQUE KEY uk_cart_customer_store (customer_id, store_id)
);

-- changeset ERP-ORDER:20261128190000-2
-- A line = item + quantity; updated_date drives the 90-day clean-up unless keep is set.
CREATE TABLE cart_line
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id      BIGINT      NOT NULL,
    item_id      BIGINT      NOT NULL,
    quantity     INT         NOT NULL,
    keep_line    BIT(1)      NOT NULL DEFAULT 0,
    created_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL,
    UNIQUE KEY uk_cart_line_item (cart_id, item_id),
    INDEX idx_cart_line_cleanup (keep_line, updated_date),
    CONSTRAINT fk_cart_line_cart FOREIGN KEY (cart_id) REFERENCES cart (id) ON DELETE CASCADE
);

-- changeset ERP-ORDER:20261128190000-3
-- Scheduled job locks (ShedLock): one row per job, so a job runs on one instance at a time.
CREATE TABLE shedlock
(
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
