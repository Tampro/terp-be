-- liquibase formatted sql

-- changeset ERP-RECRUITMENT:20261122900000-1
-- Track application (career page): a random 12-character id given to the applicant. Candidates saved before this
-- change keep NULL (they were never told an id, so there is nothing to backfill). job_posting.status VARCHAR(10)
-- already holds the new value CLOSED.
ALTER TABLE candidate
    ADD COLUMN tracking_id VARCHAR(20) NULL AFTER application_date,
    ADD CONSTRAINT uk_candidate_tracking_id UNIQUE (tracking_id);

-- changeset ERP-RECRUITMENT:20261122900000-2
-- Offer approval, separate from the offer status: PENDING / APPROVED / REJECTED; existing offers start PENDING.
-- approval_decided_by is the Keycloak subject of the user who approved or rejected.
ALTER TABLE offer
    ADD COLUMN approval_status     VARCHAR(10) NOT NULL DEFAULT 'PENDING' AFTER status,
    ADD COLUMN approval_decided_by VARCHAR(64) NULL AFTER approval_status,
    ADD COLUMN approval_decided_at DATETIME(6) NULL AFTER approval_decided_by;

-- changeset ERP-RECRUITMENT:20261122900000-3
-- Custom question types (the 7 of TERP Core custom_questions.type); existing questions are TEXT. Choice types
-- (SELECT, RADIO, CHECKBOX) list their options in custom_question_option.
ALTER TABLE custom_question
    ADD COLUMN type VARCHAR(10) NOT NULL DEFAULT 'TEXT' AFTER question;

CREATE TABLE custom_question_option
(
    custom_question_id BIGINT       NOT NULL,
    position           INT          NOT NULL,
    option_value       VARCHAR(255) NOT NULL,
    PRIMARY KEY (custom_question_id, position),
    CONSTRAINT fk_custom_question_option FOREIGN KEY (custom_question_id) REFERENCES custom_question (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-RECRUITMENT:20261122900000-4
-- Responsible role of an onboarding checklist item, free text like TERP Core checklist_items.assigned_to_role.
ALTER TABLE checklist_item
    ADD COLUMN assigned_to_role VARCHAR(100) NULL AFTER category;
