-- liquibase formatted sql

-- changeset ERP-RECRUITMENT:20261120300000-1
-- Interviews (docs/analysis/06 §7.1.3). job_posting_id is the candidate's posting (the round belongs to it); kept here
-- for filters. Times are the company's wall-clock (date + start time, no zone).
CREATE TABLE interview
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT        NOT NULL,
    candidate_id      BIGINT        NOT NULL,
    job_posting_id    BIGINT        NOT NULL,
    round_id          BIGINT        NOT NULL,
    interview_type_id BIGINT        NOT NULL,
    interview_date    DATE          NOT NULL,
    start_time        TIME          NOT NULL,
    duration_minutes  INT           NOT NULL,
    location          VARCHAR(255)  NULL,
    meeting_link      VARCHAR(1000) NULL,
    status            VARCHAR(12)   NOT NULL DEFAULT 'SCHEDULED',
    deleted           BIT(1)        NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)   NULL,
    created_date      DATETIME(6)   NULL,
    updated_by        VARCHAR(64)   NULL,
    updated_date      DATETIME(6)   NULL,
    CONSTRAINT fk_interview_candidate FOREIGN KEY (candidate_id) REFERENCES candidate (id),
    CONSTRAINT fk_interview_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting (id),
    CONSTRAINT fk_interview_round FOREIGN KEY (round_id) REFERENCES interview_round (id),
    CONSTRAINT fk_interview_type FOREIGN KEY (interview_type_id) REFERENCES recruitment_catalog (id),
    INDEX idx_interview_company_date (company_id, interview_date),
    INDEX idx_interview_candidate (candidate_id)
);

-- changeset ERP-RECRUITMENT:20261120300000-2
-- Assigned interviewers: employee-service ids with the name / email / designation copied when scheduled.
CREATE TABLE interview_interviewer
(
    interview_id BIGINT       NOT NULL,
    position     INT          NOT NULL,
    employee_id  BIGINT       NOT NULL,
    name         VARCHAR(200) NULL,
    email        VARCHAR(255) NULL,
    designation  VARCHAR(150) NULL,
    PRIMARY KEY (interview_id, position),
    CONSTRAINT fk_interviewer_interview FOREIGN KEY (interview_id) REFERENCES interview (id),
    INDEX idx_interviewer_employee (employee_id)
);

-- changeset ERP-RECRUITMENT:20261120300000-3
-- One feedback per interviewer and interview; scores 1-5.
CREATE TABLE interview_feedback
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT        NOT NULL,
    interview_id         BIGINT        NOT NULL,
    employee_id          BIGINT        NOT NULL,
    interviewer_name     VARCHAR(200)  NULL,
    interviewer_role     VARCHAR(150)  NULL,
    overall_score        INT           NOT NULL,
    technical_score      INT           NOT NULL,
    communication_score  INT           NOT NULL,
    cultural_fit_score   INT           NOT NULL,
    recommendation       VARCHAR(14)   NOT NULL,
    strengths            VARCHAR(5000) NULL,
    weaknesses           VARCHAR(5000) NULL,
    comments             VARCHAR(5000) NULL,
    created_by           VARCHAR(64)   NULL,
    created_date         DATETIME(6)   NULL,
    updated_by           VARCHAR(64)   NULL,
    updated_date         DATETIME(6)   NULL,
    CONSTRAINT fk_feedback_interview FOREIGN KEY (interview_id) REFERENCES interview (id),
    CONSTRAINT uk_feedback_interviewer UNIQUE (interview_id, employee_id)
);
