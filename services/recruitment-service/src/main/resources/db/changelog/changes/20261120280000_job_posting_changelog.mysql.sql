-- liquibase formatted sql

-- changeset ERP-RECRUITMENT:20261120280000-1
-- Next job code of each company (JOB-0001, JOB-0002, ...); the row lock serialises concurrent creates.
CREATE TABLE recruitment_sequence
(
    company_id BIGINT      NOT NULL,
    name       VARCHAR(20) NOT NULL,
    next_value BIGINT      NOT NULL,
    PRIMARY KEY (company_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-RECRUITMENT:20261120280000-2
-- Job postings (docs/analysis/06 §7.1.1, columns from doc 03 `job_postings`). branch_id / department_id are
-- employee-service ids (no foreign key across databases). description / requirements / benefits are sanitized HTML.
-- doc 03's JSON columns become plain columns (ask_* / show_*) and child tables (skills, custom questions).
CREATE TABLE job_posting
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT        NOT NULL,
    job_code             VARCHAR(20)   NOT NULL,
    title                VARCHAR(200)  NOT NULL,
    job_type_id          BIGINT        NOT NULL,
    location_id          BIGINT        NOT NULL,
    branch_id            BIGINT        NULL,
    department_id        BIGINT        NULL,
    priority             VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM',
    start_date           DATE          NULL,
    application_deadline DATE          NULL,
    application_type     VARCHAR(10)   NOT NULL DEFAULT 'EXISTING',
    application_url      VARCHAR(500)  NULL,
    positions            INT           NOT NULL DEFAULT 1,
    featured             BIT(1)        NOT NULL DEFAULT 0,
    min_experience       INT           NOT NULL DEFAULT 0,
    max_experience       INT           NULL,
    min_salary           DECIMAL(15, 2) NULL,
    max_salary           DECIMAL(15, 2) NULL,
    description          MEDIUMTEXT    NULL,
    requirements         MEDIUMTEXT    NULL,
    benefits             MEDIUMTEXT    NULL,
    ask_gender           BIT(1)        NOT NULL DEFAULT 0,
    ask_date_of_birth    BIT(1)        NOT NULL DEFAULT 0,
    show_cover_letter    BIT(1)        NOT NULL DEFAULT 0,
    show_terms           BIT(1)        NOT NULL DEFAULT 0,
    status               VARCHAR(10)   NOT NULL DEFAULT 'DRAFT',
    published            BIT(1)        NOT NULL DEFAULT 0,
    publish_date         DATETIME(6)   NULL,
    deleted              BIT(1)        NOT NULL DEFAULT 0,
    created_by           VARCHAR(64)   NULL,
    updated_by           VARCHAR(64)   NULL,
    created_date         DATETIME(6)   NULL,
    updated_date         DATETIME(6)   NULL,
    UNIQUE KEY uk_job_posting_code (company_id, job_code),
    INDEX idx_job_posting_company (company_id, status),
    CONSTRAINT fk_job_posting_type FOREIGN KEY (job_type_id) REFERENCES recruitment_catalog (id),
    CONSTRAINT fk_job_posting_location FOREIGN KEY (location_id) REFERENCES job_location (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-RECRUITMENT:20261120280000-3
CREATE TABLE job_posting_skill
(
    job_posting_id BIGINT       NOT NULL,
    position       INT          NOT NULL,
    skill          VARCHAR(100) NOT NULL,
    PRIMARY KEY (job_posting_id, position),
    CONSTRAINT fk_job_posting_skill FOREIGN KEY (job_posting_id) REFERENCES job_posting (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE job_posting_question
(
    job_posting_id     BIGINT NOT NULL,
    custom_question_id BIGINT NOT NULL,
    PRIMARY KEY (job_posting_id, custom_question_id),
    CONSTRAINT fk_job_posting_question_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting (id),
    CONSTRAINT fk_job_posting_question_question FOREIGN KEY (custom_question_id) REFERENCES custom_question (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-RECRUITMENT:20261120280000-4
-- Interview rounds of a job posting (§7.1.15, doc 03 `interview_rounds`); sequence unique per posting.
CREATE TABLE interview_round
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT        NOT NULL,
    job_posting_id  BIGINT        NOT NULL,
    name            VARCHAR(150)  NOT NULL,
    sequence_number INT           NOT NULL,
    description     VARCHAR(1000) NULL,
    status          VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    deleted         BIT(1)        NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)   NULL,
    updated_by      VARCHAR(64)   NULL,
    created_date    DATETIME(6)   NULL,
    updated_date    DATETIME(6)   NULL,
    INDEX idx_interview_round_posting (job_posting_id, sequence_number),
    CONSTRAINT fk_interview_round_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
