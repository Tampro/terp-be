-- liquibase formatted sql

-- changeset ERP-RECRUITMENT:20261120290000-1
-- Candidates (docs/analysis/06 §7.1.2, columns from doc 03 `candidates`). The posting gives branch, department,
-- location and job type (not copied). resume_* / cover_letter_* describe files kept on the service's own volume under a
-- server-generated key. employee_id is set when onboarding converts the candidate (employee-service id).
CREATE TABLE candidate
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT        NOT NULL,
    job_posting_id       BIGINT        NOT NULL,
    source_id            BIGINT        NULL,
    first_name           VARCHAR(100)  NOT NULL,
    last_name            VARCHAR(100)  NOT NULL,
    email                VARCHAR(255)  NOT NULL,
    phone                VARCHAR(30)   NULL,
    current_company      VARCHAR(150)  NULL,
    current_position     VARCHAR(150)  NULL,
    experience_years     INT           NOT NULL DEFAULT 0,
    current_salary       DECIMAL(15, 2) NULL,
    expected_salary      DECIMAL(15, 2) NULL,
    notice_period        VARCHAR(50)   NULL,
    gender               VARCHAR(10)   NULL,
    date_of_birth        DATE          NULL,
    address              VARCHAR(255)  NULL,
    city                 VARCHAR(100)  NULL,
    state                VARCHAR(100)  NULL,
    zip_code             VARCHAR(20)   NULL,
    country              VARCHAR(100)  NULL,
    cover_letter_message VARCHAR(5000) NULL,
    terms_accepted       BIT(1)        NOT NULL DEFAULT 0,
    status               VARCHAR(12)   NOT NULL DEFAULT 'NEW',
    application_date     DATE          NOT NULL,
    employee_id          BIGINT        NULL,
    resume_key           VARCHAR(64)   NULL,
    resume_name          VARCHAR(255)  NULL,
    resume_mime          VARCHAR(127)  NULL,
    resume_size          BIGINT        NULL,
    deleted              BIT(1)        NOT NULL DEFAULT 0,
    created_by           VARCHAR(64)   NULL,
    updated_by           VARCHAR(64)   NULL,
    created_date         DATETIME(6)   NULL,
    updated_date         DATETIME(6)   NULL,
    INDEX idx_candidate_company_status (company_id, status),
    INDEX idx_candidate_posting (job_posting_id),
    INDEX idx_candidate_email (company_id, email),
    CONSTRAINT fk_candidate_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting (id),
    CONSTRAINT fk_candidate_source FOREIGN KEY (source_id) REFERENCES recruitment_catalog (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-RECRUITMENT:20261120290000-2
-- Answers to the posting's custom questions; the question text is copied so a later edit does not change the answer.
CREATE TABLE candidate_answer
(
    candidate_id       BIGINT        NOT NULL,
    position           INT           NOT NULL,
    custom_question_id BIGINT        NOT NULL,
    question           VARCHAR(1000) NOT NULL,
    answer             VARCHAR(5000) NULL,
    PRIMARY KEY (candidate_id, position),
    CONSTRAINT fk_candidate_answer_candidate FOREIGN KEY (candidate_id) REFERENCES candidate (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
