-- liquibase formatted sql

-- changeset ERP-ACCOUNTING:20261122600000-1
-- Financial goals (manual 14): categories, goals, milestones and manual contributions. Milestone status, automatic
-- contributions and tracking are computed on read from the ledger, so nothing of them is stored.
CREATE TABLE goal_category
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    code         VARCHAR(30)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_goal_category_company (company_id, code)
);

CREATE TABLE financial_goal
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT         NOT NULL,
    category_id   BIGINT         NULL,
    name          VARCHAR(255)   NOT NULL,
    goal_type     VARCHAR(30)    NOT NULL,
    priority      VARCHAR(20)    NOT NULL,
    target_amount DECIMAL(19, 4) NOT NULL,
    start_date    DATE           NOT NULL,
    target_date   DATE           NOT NULL,
    account_id    BIGINT         NULL,
    description   VARCHAR(1000)  NULL,
    status        VARCHAR(20)    NOT NULL,
    completed_at  DATETIME(6)    NULL,
    deleted       BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by    VARCHAR(64)    NULL,
    created_date  DATETIME(6)    NULL,
    updated_by    VARCHAR(64)    NULL,
    updated_date  DATETIME(6)    NULL,
    INDEX idx_financial_goal_company (company_id, status),
    CONSTRAINT fk_financial_goal_category FOREIGN KEY (category_id) REFERENCES goal_category (id),
    CONSTRAINT fk_financial_goal_account FOREIGN KEY (account_id) REFERENCES chart_account (id)
);

CREATE TABLE goal_milestone
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    goal_id       BIGINT         NOT NULL,
    name          VARCHAR(255)   NOT NULL,
    target_amount DECIMAL(19, 4) NOT NULL,
    target_date   DATE           NOT NULL,
    created_by    VARCHAR(64)    NULL,
    created_date  DATETIME(6)    NULL,
    updated_by    VARCHAR(64)    NULL,
    updated_date  DATETIME(6)    NULL,
    CONSTRAINT fk_goal_milestone_goal FOREIGN KEY (goal_id) REFERENCES financial_goal (id)
);

CREATE TABLE goal_contribution
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    goal_id           BIGINT         NOT NULL,
    contribution_date DATE           NOT NULL,
    amount            DECIMAL(19, 4) NOT NULL,
    notes             VARCHAR(500)   NULL,
    created_by        VARCHAR(64)    NULL,
    created_date      DATETIME(6)    NULL,
    updated_by        VARCHAR(64)    NULL,
    updated_date      DATETIME(6)    NULL,
    INDEX idx_goal_contribution_goal (goal_id, contribution_date),
    CONSTRAINT fk_goal_contribution_goal FOREIGN KEY (goal_id) REFERENCES financial_goal (id)
);

-- changeset ERP-ACCOUNTING:20261122600000-2
-- Budgets (manual 14, FR-BUD-001..004): periods, budgets, allocations per expense account. Spent is computed on
-- read from counted journal lines, never stored.
CREATE TABLE budget_period
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT       NOT NULL,
    name           VARCHAR(255) NOT NULL,
    financial_year INT          NOT NULL,
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    closed_at      DATETIME(6)  NULL,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by     VARCHAR(64)  NULL,
    created_date   DATETIME(6)  NULL,
    updated_by     VARCHAR(64)  NULL,
    updated_date   DATETIME(6)  NULL,
    INDEX idx_budget_period_company (company_id, financial_year)
);

CREATE TABLE budget
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    period_id    BIGINT         NOT NULL,
    name         VARCHAR(255)   NOT NULL,
    budget_type  VARCHAR(20)    NOT NULL,
    amount       DECIMAL(19, 4) NOT NULL,
    description  VARCHAR(1000)  NULL,
    status       VARCHAR(20)    NOT NULL,
    approved_by  BIGINT         NULL,
    approved_at  DATETIME(6)    NULL,
    deleted      BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_by   VARCHAR(64)    NULL,
    updated_date DATETIME(6)    NULL,
    INDEX idx_budget_company (company_id, period_id, status),
    CONSTRAINT fk_budget_period FOREIGN KEY (period_id) REFERENCES budget_period (id)
);

CREATE TABLE budget_allocation
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    budget_id        BIGINT         NOT NULL,
    account_id       BIGINT         NOT NULL,
    allocated_amount DECIMAL(19, 4) NOT NULL,
    notes            VARCHAR(500)   NULL,
    created_by       VARCHAR(64)    NULL,
    created_date     DATETIME(6)    NULL,
    updated_by       VARCHAR(64)    NULL,
    updated_date     DATETIME(6)    NULL,
    UNIQUE KEY uk_budget_allocation_account (budget_id, account_id),
    CONSTRAINT fk_budget_allocation_budget FOREIGN KEY (budget_id) REFERENCES budget (id),
    CONSTRAINT fk_budget_allocation_account FOREIGN KEY (account_id) REFERENCES chart_account (id)
);
