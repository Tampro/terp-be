-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120410000-1
-- Employee reviews (docs/analysis/06 7.3.1). overall_rating = weighted average of the indicator ratings (1-5),
-- 1 decimal (PO, 2026-11-20).
CREATE TABLE employee_review
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT        NOT NULL,
    employee_id      BIGINT        NOT NULL,
    reviewer_id      BIGINT        NOT NULL,
    review_cycle_id  BIGINT        NOT NULL,
    review_date      DATE          NOT NULL,
    status           VARCHAR(12)   NOT NULL DEFAULT 'SCHEDULED',
    overall_rating   DECIMAL(3, 1) NULL,
    overall_comments TEXT          NULL,
    completed_at     DATETIME(6)   NULL,
    deleted          BIT(1)        NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_date     DATETIME(6)   NULL,
    updated_by       VARCHAR(64)   NULL,
    updated_date     DATETIME(6)   NULL,
    INDEX idx_employee_review_company (company_id, review_date),
    INDEX idx_employee_review_employee (employee_id),
    CONSTRAINT fk_employee_review_cycle FOREIGN KEY (review_cycle_id) REFERENCES review_cycle (id)
);

-- changeset ERP-EMPLOYEE:20261120410000-2
-- One row per indicator, snapshotted from the company's active indicators the first time the review is conducted,
-- so later edits or deletions of an indicator do not change a review. indicator_id is kept without a foreign key.
CREATE TABLE review_rating
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id             BIGINT        NOT NULL,
    indicator_id          BIGINT        NOT NULL,
    category_name         VARCHAR(150)  NULL,
    indicator_name        VARCHAR(150)  NOT NULL,
    indicator_description VARCHAR(1000) NULL,
    measurement_unit      VARCHAR(50)   NULL,
    target_value          VARCHAR(100)  NULL,
    weight                DECIMAL(6, 2) NOT NULL,
    rating                INT           NULL,
    comments              TEXT          NULL,
    created_by            VARCHAR(64)   NULL,
    created_date          DATETIME(6)   NULL,
    updated_by            VARCHAR(64)   NULL,
    updated_date          DATETIME(6)   NULL,
    UNIQUE KEY uk_review_rating (review_id, indicator_id),
    CONSTRAINT fk_review_rating_review FOREIGN KEY (review_id) REFERENCES employee_review (id)
);
