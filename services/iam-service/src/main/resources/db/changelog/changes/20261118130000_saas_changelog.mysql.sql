-- liquibase formatted sql

-- changeset ERP-IAM:20261118130000-1
CREATE TABLE plan
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(50)    NOT NULL,
    name             VARCHAR(100)   NOT NULL,
    description      VARCHAR(255)   NULL,
    monthly_price    DECIMAL(15, 2) NOT NULL,
    yearly_price     DECIMAL(15, 2) NULL,
    max_users        INT            NULL,
    max_employees    INT            NULL,
    storage_limit_gb DECIMAL(10, 2) NULL,
    trial_days       INT            NOT NULL DEFAULT 0,
    is_default       BIT(1)         NOT NULL DEFAULT 0,
    enabled          BIT(1)         NOT NULL DEFAULT 1,
    deleted          BIT(1)         NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)    NULL,
    updated_by       VARCHAR(64)    NULL,
    created_date     DATETIME(6)    NULL,
    updated_date     DATETIME(6)    NULL,
    INDEX idx_plan_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261118130000-2
CREATE TABLE coupon
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                  VARCHAR(50)    NOT NULL,
    name                  VARCHAR(100)   NOT NULL,
    type                  VARCHAR(20)    NOT NULL,
    discount_value        DECIMAL(15, 2) NOT NULL,
    minimum_spend         DECIMAL(15, 2) NULL,
    use_limit_total       INT            NULL,
    use_limit_per_company INT            NULL,
    expiry_date           DATE           NULL,
    enabled               BIT(1)         NOT NULL DEFAULT 1,
    deleted               BIT(1)         NOT NULL DEFAULT 0,
    created_by            VARCHAR(64)    NULL,
    updated_by            VARCHAR(64)    NULL,
    created_date          DATETIME(6)    NULL,
    updated_date          DATETIME(6)    NULL,
    INDEX idx_coupon_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261118130000-3
CREATE TABLE plan_order
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number      VARCHAR(30)    NOT NULL,
    company_id        BIGINT         NOT NULL,
    plan_id           BIGINT         NOT NULL,
    billing_cycle     VARCHAR(20)    NOT NULL,
    coupon_id         BIGINT         NULL,
    coupon_code       VARCHAR(50)    NULL,
    original_price    DECIMAL(15, 2) NOT NULL,
    discount_amount   DECIMAL(15, 2) NOT NULL,
    final_price       DECIMAL(15, 2) NOT NULL,
    payment_method    VARCHAR(30)    NOT NULL,
    payment_reference VARCHAR(100)   NULL,
    status            VARCHAR(20)    NOT NULL,
    processed_at      DATETIME(6)    NULL,
    processed_by      VARCHAR(64)    NULL,
    notes             VARCHAR(500)   NULL,
    created_by        VARCHAR(64)    NULL,
    updated_by        VARCHAR(64)    NULL,
    created_date      DATETIME(6)    NULL,
    updated_date      DATETIME(6)    NULL,
    CONSTRAINT uk_plan_order_number UNIQUE (order_number),
    INDEX idx_plan_order_company (company_id, status),
    INDEX idx_plan_order_coupon (coupon_id, status),
    CONSTRAINT fk_plan_order_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_plan_order_plan FOREIGN KEY (plan_id) REFERENCES plan (id),
    CONSTRAINT fk_plan_order_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261118130000-4
-- Existing companies keep plan_id NULL = no subscription (no limits), as before this change.
ALTER TABLE company
    ADD COLUMN plan_id         BIGINT      NULL,
    ADD COLUMN plan_expires_at DATETIME(6) NULL,
    ADD COLUMN on_trial        BIT(1)      NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_company_plan FOREIGN KEY (plan_id) REFERENCES plan (id);
