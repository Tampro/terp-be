-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120450000-1
-- Training assessments (docs/analysis/06 7.4.4) of a program; passing_score in %.
CREATE TABLE training_assessment
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT        NOT NULL,
    program_id    BIGINT        NOT NULL,
    name          VARCHAR(200)  NOT NULL,
    description   TEXT          NULL,
    type          VARCHAR(12)   NOT NULL,
    passing_score DECIMAL(5, 2) NOT NULL,
    criteria      TEXT          NULL,
    deleted       BIT(1)        NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)   NULL,
    created_date  DATETIME(6)   NULL,
    updated_by    VARCHAR(64)   NULL,
    updated_date  DATETIME(6)   NULL,
    INDEX idx_training_assessment_company (company_id, program_id),
    CONSTRAINT fk_training_assessment_program FOREIGN KEY (program_id) REFERENCES training_program (id)
);

-- changeset ERP-EMPLOYEE:20261120450000-2
-- One result per assessment and employee training (PO, 2026-11-20); passed is derived from the passing score.
CREATE TABLE assessment_result
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    assessment_id        BIGINT        NOT NULL,
    employee_training_id BIGINT        NOT NULL,
    score                DECIMAL(5, 2) NOT NULL,
    assessed_date        DATE          NULL,
    notes                TEXT          NULL,
    created_by           VARCHAR(64)   NULL,
    created_date         DATETIME(6)   NULL,
    updated_by           VARCHAR(64)   NULL,
    updated_date         DATETIME(6)   NULL,
    UNIQUE KEY uk_assessment_result (assessment_id, employee_training_id),
    INDEX idx_assessment_result_training (employee_training_id),
    CONSTRAINT fk_assessment_result_assessment FOREIGN KEY (assessment_id) REFERENCES training_assessment (id),
    CONSTRAINT fk_assessment_result_training FOREIGN KEY (employee_training_id) REFERENCES employee_training (id)
);
