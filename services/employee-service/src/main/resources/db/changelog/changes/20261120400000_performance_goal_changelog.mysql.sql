-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120400000-1
-- Employee goals (docs/analysis/06 7.3.2); goal type from the company's goal-types list; progress 0-100.
CREATE TABLE employee_goal
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    employee_id  BIGINT       NOT NULL,
    goal_type_id BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    description  TEXT         NULL,
    start_date   DATE         NOT NULL,
    end_date     DATE         NOT NULL,
    target       VARCHAR(200) NULL,
    progress     INT          NOT NULL DEFAULT 0,
    status       VARCHAR(12)  NOT NULL DEFAULT 'NOT_STARTED',
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_employee_goal_company (company_id, start_date),
    INDEX idx_employee_goal_employee (employee_id),
    CONSTRAINT fk_employee_goal_type FOREIGN KEY (goal_type_id) REFERENCES performance_catalog (id)
);
