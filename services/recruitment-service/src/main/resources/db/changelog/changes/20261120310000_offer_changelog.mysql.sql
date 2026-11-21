-- liquibase formatted sql

-- changeset ERP-RECRUITMENT:20261120310000-1
-- Offer letter templates (docs/analysis/06 §7.1.16): sanitized HTML with {{variable}} placeholders; variables is the
-- declared list, in order.
CREATE TABLE offer_template
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(150) NOT NULL,
    content      MEDIUMTEXT   NOT NULL,
    status       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_offer_template_company (company_id)
);

-- changeset ERP-RECRUITMENT:20261120310000-2
CREATE TABLE offer_template_variable
(
    template_id BIGINT      NOT NULL,
    position    INT         NOT NULL,
    name        VARCHAR(64) NOT NULL,
    PRIMARY KEY (template_id, position),
    CONSTRAINT fk_offer_template_variable FOREIGN KEY (template_id) REFERENCES offer_template (id)
);

-- changeset ERP-RECRUITMENT:20261120310000-3
-- Offers (docs/analysis/06 §7.1.4). job_posting_id = the position offered (defaults to the candidate's posting);
-- department_id is an employee-service id; approved_by_* an employee-service employee, name copied.
CREATE TABLE offer
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id             BIGINT         NOT NULL,
    candidate_id           BIGINT         NOT NULL,
    job_posting_id         BIGINT         NOT NULL,
    department_id          BIGINT         NULL,
    salary                 DECIMAL(15, 2) NOT NULL,
    start_date             DATE           NULL,
    expiration_date        DATE           NULL,
    approved_by_employee_id BIGINT        NULL,
    approved_by_name       VARCHAR(200)   NULL,
    benefits               VARCHAR(5000)  NULL,
    status                 VARCHAR(12)    NOT NULL DEFAULT 'DRAFT',
    offer_date             DATE           NOT NULL,
    deleted                BIT(1)         NOT NULL DEFAULT 0,
    created_by             VARCHAR(64)    NULL,
    created_date           DATETIME(6)    NULL,
    updated_by             VARCHAR(64)    NULL,
    updated_date           DATETIME(6)    NULL,
    CONSTRAINT fk_offer_candidate FOREIGN KEY (candidate_id) REFERENCES candidate (id),
    CONSTRAINT fk_offer_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting (id),
    INDEX idx_offer_company (company_id, status)
);
