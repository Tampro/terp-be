-- liquibase formatted sql

-- changeset ERP-RECRUITMENT:20261120320000-1
-- Onboarding checklists (docs/analysis/06 §7.1.7); at most one default per company (kept by the service).
CREATE TABLE onboarding_checklist
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(150) NOT NULL,
    description  VARCHAR(1000) NULL,
    is_default   BIT(1)       NOT NULL DEFAULT 0,
    status       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_onboarding_checklist_company (company_id)
);

-- changeset ERP-RECRUITMENT:20261120320000-2
-- Checklist items (§7.1.8). due_day = days after the onboarding start date (PO, 2026-11-20).
CREATE TABLE checklist_item
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT        NOT NULL,
    checklist_id BIGINT        NOT NULL,
    task_name    VARCHAR(200)  NOT NULL,
    description  VARCHAR(1000) NULL,
    category     VARCHAR(20)   NOT NULL,
    is_required  BIT(1)        NOT NULL DEFAULT 0,
    due_day      INT           NULL,
    status       VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    deleted      BIT(1)        NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_date DATETIME(6)   NULL,
    updated_by   VARCHAR(64)   NULL,
    updated_date DATETIME(6)   NULL,
    CONSTRAINT fk_checklist_item_checklist FOREIGN KEY (checklist_id) REFERENCES onboarding_checklist (id),
    INDEX idx_checklist_item_company (company_id)
);

-- changeset ERP-RECRUITMENT:20261120320000-3
-- Candidate onboarding (§7.1.5): an existing employee-service employee (name / email copied), optionally linked to
-- the hired candidate it came from (candidate.employee_id is set then).
CREATE TABLE candidate_onboarding
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT       NOT NULL,
    employee_id       BIGINT       NOT NULL,
    employee_name     VARCHAR(200) NULL,
    employee_email    VARCHAR(255) NULL,
    candidate_id      BIGINT       NULL,
    checklist_id      BIGINT       NOT NULL,
    start_date        DATE         NOT NULL,
    buddy_employee_id BIGINT       NULL,
    buddy_name        VARCHAR(200) NULL,
    status            VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
    deleted           BIT(1)       NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)  NULL,
    created_date      DATETIME(6)  NULL,
    updated_by        VARCHAR(64)  NULL,
    updated_date      DATETIME(6)  NULL,
    CONSTRAINT fk_onboarding_checklist FOREIGN KEY (checklist_id) REFERENCES onboarding_checklist (id),
    CONSTRAINT fk_onboarding_candidate FOREIGN KEY (candidate_id) REFERENCES candidate (id),
    INDEX idx_onboarding_company (company_id, status),
    INDEX idx_onboarding_employee (employee_id)
);

-- changeset ERP-RECRUITMENT:20261120320000-4
-- Candidate assessments (§7.1.6); conducted_by_* an employee, name copied.
CREATE TABLE candidate_assessment
(
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id               BIGINT        NOT NULL,
    candidate_id             BIGINT        NOT NULL,
    name                     VARCHAR(200)  NOT NULL,
    score                    DECIMAL(9, 2) NOT NULL,
    max_score                DECIMAL(9, 2) NOT NULL,
    status                   VARCHAR(8)    NOT NULL DEFAULT 'PENDING',
    conducted_by_employee_id BIGINT        NULL,
    conducted_by_name        VARCHAR(200)  NULL,
    assessment_date          DATE          NOT NULL,
    comments                 VARCHAR(5000) NULL,
    deleted                  BIT(1)        NOT NULL DEFAULT 0,
    created_by               VARCHAR(64)   NULL,
    created_date             DATETIME(6)   NULL,
    updated_by               VARCHAR(64)   NULL,
    updated_date             DATETIME(6)   NULL,
    CONSTRAINT fk_assessment_candidate FOREIGN KEY (candidate_id) REFERENCES candidate (id),
    INDEX idx_assessment_company (company_id, status)
);
