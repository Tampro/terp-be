-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120380000-1
-- Complaints (docs/analysis/06 7.2.8). Complainant, accused and handler are employees of the company; type and
-- resolution type come from the company's lists. An anonymous complaint keeps its complainant (needed for the
-- follow-up feedback) but shows it only to LIFECYCLE_MANAGE.
CREATE TABLE employee_complaint
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    complainant_id      BIGINT       NOT NULL,
    against_id          BIGINT       NULL,
    complaint_type_id   BIGINT       NOT NULL,
    subject             VARCHAR(200) NOT NULL,
    complaint_date      DATE         NOT NULL,
    description         TEXT         NULL,
    document_media_id   BIGINT       NULL,
    anonymous           BIT(1)       NOT NULL DEFAULT 0,
    status              VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    assigned_to_id      BIGINT       NULL,
    resolution_deadline DATE         NULL,
    resolution_type_id  BIGINT       NULL,
    investigation_notes TEXT         NULL,
    resolution_action   TEXT         NULL,
    resolution_date     DATE         NULL,
    follow_up_action    TEXT         NULL,
    follow_up_date      DATE         NULL,
    feedback            TEXT         NULL,
    deleted             BIT(1)       NOT NULL DEFAULT 0,
    created_by          VARCHAR(64)  NULL,
    created_date        DATETIME(6)  NULL,
    updated_by          VARCHAR(64)  NULL,
    updated_date        DATETIME(6)  NULL,
    INDEX idx_employee_complaint_company (company_id, complaint_date),
    CONSTRAINT fk_employee_complaint_type FOREIGN KEY (complaint_type_id) REFERENCES lifecycle_catalog (id),
    CONSTRAINT fk_employee_complaint_resolution FOREIGN KEY (resolution_type_id) REFERENCES lifecycle_catalog (id)
);
