-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120430000-1
-- Employee trainings (docs/analysis/06 7.4.1): one assignment of an employee to a program; assigned_by = the
-- assigning user's employee profile, when they have one. Score 0-100; passed null = no result yet.
CREATE TABLE employee_training
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT        NOT NULL,
    employee_id          BIGINT        NOT NULL,
    program_id           BIGINT        NOT NULL,
    status               VARCHAR(12)   NOT NULL DEFAULT 'ASSIGNED',
    assigned_date        DATE          NOT NULL,
    completion_date      DATE          NULL,
    assigned_by_id       BIGINT        NULL,
    certificate_media_id BIGINT        NULL,
    score                DECIMAL(5, 2) NULL,
    passed               BIT(1)        NULL,
    feedback             TEXT          NULL,
    notes                TEXT          NULL,
    deleted              BIT(1)        NOT NULL DEFAULT 0,
    created_by           VARCHAR(64)   NULL,
    created_date         DATETIME(6)   NULL,
    updated_by           VARCHAR(64)   NULL,
    updated_date         DATETIME(6)   NULL,
    INDEX idx_employee_training_company (company_id, assigned_date),
    INDEX idx_employee_training_program (program_id),
    INDEX idx_employee_training_employee (employee_id),
    CONSTRAINT fk_employee_training_program FOREIGN KEY (program_id) REFERENCES training_program (id)
);
