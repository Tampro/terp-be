-- liquibase formatted sql

-- changeset ERP-IAM:20261119140000-1
-- Feature add-on flag on the plan card (docs/analysis/06 §3.2.3); existing plans: off.
ALTER TABLE plan
    ADD COLUMN ai_integration BIT(1) NOT NULL DEFAULT 0;

-- changeset ERP-IAM:20261119140000-2
CREATE TABLE plan_request
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    plan_id       BIGINT       NOT NULL,
    billing_cycle VARCHAR(20)  NOT NULL,
    message       VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL,
    processed_at  DATETIME(6)  NULL,
    processed_by  VARCHAR(64)  NULL,
    notes         VARCHAR(500) NULL,
    order_id      BIGINT       NULL,
    created_by    VARCHAR(64)  NULL,
    updated_by    VARCHAR(64)  NULL,
    created_date  DATETIME(6)  NULL,
    updated_date  DATETIME(6)  NULL,
    INDEX idx_plan_request_company (company_id, status),
    INDEX idx_plan_request_status (status),
    CONSTRAINT fk_plan_request_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_plan_request_plan FOREIGN KEY (plan_id) REFERENCES plan (id),
    CONSTRAINT fk_plan_request_order FOREIGN KEY (order_id) REFERENCES plan_order (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261119140000-3
-- Plan Orders page filters on order date.
CREATE INDEX idx_plan_order_created ON plan_order (created_date);
