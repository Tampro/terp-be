-- liquibase formatted sql

-- changeset ERP-MEETING:20261120540000-1
-- Action items of meetings (docs/analysis/06 9.1.2). status follows progress (0 / 1-99 / 100); Overdue is computed
-- from due_date at read time. The assignee lives in employee-service; name / email copied for search.
CREATE TABLE action_item
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT        NOT NULL,
    meeting_id     BIGINT        NOT NULL,
    title          VARCHAR(200)  NOT NULL,
    description    VARCHAR(2000) NULL,
    assignee_id    BIGINT        NOT NULL,
    assignee_name  VARCHAR(200)  NULL,
    assignee_email VARCHAR(255)  NULL,
    due_date       DATE          NULL,
    priority       VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM',
    progress       INT           NOT NULL DEFAULT 0,
    progress_notes VARCHAR(2000) NULL,
    status         VARCHAR(12)   NOT NULL DEFAULT 'NOT_STARTED',
    deleted        BIT(1)        NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_date   DATETIME(6)   NULL,
    updated_by     VARCHAR(64)   NULL,
    updated_date   DATETIME(6)   NULL,
    INDEX idx_action_item_company (company_id, status, due_date),
    INDEX idx_action_item_meeting (meeting_id),
    INDEX idx_action_item_assignee (assignee_id),
    CONSTRAINT fk_action_item_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id)
);
