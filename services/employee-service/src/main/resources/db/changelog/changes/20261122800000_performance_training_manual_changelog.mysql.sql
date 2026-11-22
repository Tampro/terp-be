-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261122800000-1
-- Conduct Review: Pros / Cons of the review (manual 16, FR-PERF-002).
ALTER TABLE employee_review
    ADD COLUMN pros TEXT NULL AFTER overall_comments,
    ADD COLUMN cons TEXT NULL AFTER pros,
    ALTER COLUMN status SET DEFAULT 'PENDING';

-- changeset ERP-EMPLOYEE:20261122800000-2
-- Trainers (FR-TRN-002): people who run a training, not necessarily employees. department_id belongs to branch_id.
CREATE TABLE trainer
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT       NOT NULL,
    name             VARCHAR(150) NOT NULL,
    contact_phone    VARCHAR(30)  NULL,
    email            VARCHAR(150) NULL,
    experience_years INT          NULL,
    branch_id        BIGINT       NULL,
    department_id    BIGINT       NULL,
    expertise        VARCHAR(500) NULL,
    qualification    VARCHAR(500) NULL,
    active           BIT(1)       NOT NULL DEFAULT 1,
    deleted          BIT(1)       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NULL,
    created_date     DATETIME(6)  NULL,
    updated_by       VARCHAR(64)  NULL,
    updated_date     DATETIME(6)  NULL,
    INDEX idx_trainer_company (company_id, deleted, name)
);

-- changeset ERP-EMPLOYEE:20261122800000-3
-- A training session may name one trainer of the trainer list, besides the employees conducting it.
ALTER TABLE training_session
    ADD COLUMN trainer_id BIGINT NULL AFTER series_id,
    ADD CONSTRAINT fk_training_session_trainer_ref FOREIGN KEY (trainer_id) REFERENCES trainer (id);
