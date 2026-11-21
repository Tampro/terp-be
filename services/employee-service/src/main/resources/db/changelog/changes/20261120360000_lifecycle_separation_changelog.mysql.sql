-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120360000-1
-- Resignations (§7.2.5). COMPLETED terminates the employee (and locks the login) at once and locks the record.
CREATE TABLE employee_resignation
(
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id               BIGINT       NOT NULL,
    employee_id              BIGINT       NOT NULL,
    resignation_date         DATE         NOT NULL,
    last_working_day         DATE         NULL,
    notice_period            VARCHAR(50)  NULL,
    reason                   VARCHAR(500) NULL,
    description              TEXT         NULL,
    document_media_id        BIGINT       NULL,
    status                   VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
    exit_interview_conducted BIT(1)       NOT NULL DEFAULT 0,
    exit_interview_date      DATE         NULL,
    exit_feedback            TEXT         NULL,
    completed_at             DATETIME(6)  NULL,
    deleted                  BIT(1)       NOT NULL DEFAULT 0,
    created_by               VARCHAR(64)  NULL,
    created_date             DATETIME(6)  NULL,
    updated_by               VARCHAR(64)  NULL,
    updated_date             DATETIME(6)  NULL,
    INDEX idx_employee_resignation_company (company_id, resignation_date),
    INDEX idx_employee_resignation_employee (employee_id)
);

-- changeset ERP-EMPLOYEE:20261120360000-2
-- Terminations (§7.2.6); type from the company's termination-types list.
CREATE TABLE employee_termination
(
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id               BIGINT       NOT NULL,
    employee_id              BIGINT       NOT NULL,
    termination_type_id      BIGINT       NOT NULL,
    notice_date              DATE         NULL,
    termination_date         DATE         NOT NULL,
    notice_period            VARCHAR(50)  NULL,
    reason                   VARCHAR(500) NULL,
    description              TEXT         NULL,
    document_media_id        BIGINT       NULL,
    status                   VARCHAR(12)  NOT NULL DEFAULT 'PLANNED',
    exit_interview_conducted BIT(1)       NOT NULL DEFAULT 0,
    exit_interview_date      DATE         NULL,
    exit_feedback            TEXT         NULL,
    completed_at             DATETIME(6)  NULL,
    deleted                  BIT(1)       NOT NULL DEFAULT 0,
    created_by               VARCHAR(64)  NULL,
    created_date             DATETIME(6)  NULL,
    updated_by               VARCHAR(64)  NULL,
    updated_date             DATETIME(6)  NULL,
    INDEX idx_employee_termination_company (company_id, termination_date),
    INDEX idx_employee_termination_employee (employee_id),
    CONSTRAINT fk_employee_termination_type FOREIGN KEY (termination_type_id) REFERENCES lifecycle_catalog (id)
);
