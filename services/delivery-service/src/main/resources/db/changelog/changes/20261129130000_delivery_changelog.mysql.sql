-- liquibase formatted sql

-- changeset ERP-DELIVERY:20261129130000-1
-- One delivery per paid order (order.paid). No personal data: the address and phone stay on the order (read on demand).
CREATE TABLE delivery
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    order_id      BIGINT       NOT NULL,
    order_no      VARCHAR(30)  NOT NULL,
    store_id      BIGINT       NOT NULL,
    warehouse_id  BIGINT       NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    rider_sub     VARCHAR(64)  NULL,
    rider_name    VARCHAR(120) NULL,
    assigned_at   DATETIME(6)  NULL,
    picked_up_at  DATETIME(6)  NULL,
    delivered_at  DATETIME(6)  NULL,
    cancelled_at  DATETIME(6)  NULL,
    cancel_reason VARCHAR(200) NULL,
    version       INT          NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    updated_by    VARCHAR(64)  NULL,
    created_date  DATETIME(6)  NULL,
    updated_date  DATETIME(6)  NULL,
    UNIQUE KEY uk_delivery_company_order (company_id, order_id),
    INDEX idx_delivery_company_status (company_id, status, id),
    INDEX idx_delivery_rider (rider_sub, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-DELIVERY:20261129130000-2
-- One row per consumed order event (dedupe of at-least-once delivery).
CREATE TABLE processed_event
(
    event_id    VARCHAR(36)  NOT NULL PRIMARY KEY,
    company_id  BIGINT       NULL,
    event_type  VARCHAR(100) NOT NULL,
    received_at DATETIME(6)  NOT NULL,
    INDEX idx_processed_event_received (received_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
