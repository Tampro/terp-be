-- liquibase formatted sql

-- changeset ERP-PROJECT:20261122310000-1
-- Projects (ERP manual 11.1, FR-PRJ-001): status ONGOING / ON_HOLD / FINISHED; overdue is computed, not stored.
CREATE TABLE prj_project
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    name         VARCHAR(191)   NOT NULL,
    start_date   DATE           NULL,
    end_date     DATE           NULL,
    budget       DECIMAL(19, 4) NULL,
    description  TEXT           NULL,
    status       VARCHAR(10)    NOT NULL,
    deleted      BIT(1)         NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_prj_project_company (company_id, deleted, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-PROJECT:20261122310000-2
-- Team members: IAM user ids of the project company (checked through IAM when added).
CREATE TABLE prj_project_user
(
    project_id BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    PRIMARY KEY (project_id, user_id),
    INDEX idx_prj_project_user_user (user_id),
    CONSTRAINT fk_prj_project_user_project FOREIGN KEY (project_id) REFERENCES prj_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-PROJECT:20261122310000-3
-- Clients: accounting-service customer ids.
CREATE TABLE prj_project_client
(
    project_id  BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    PRIMARY KEY (project_id, customer_id),
    CONSTRAINT fk_prj_project_client_project FOREIGN KEY (project_id) REFERENCES prj_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-PROJECT:20261122310000-4
-- Milestones: status INCOMPLETE / ONGOING / COMPLETE, progress 0-100.
CREATE TABLE prj_milestone
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    project_id   BIGINT         NOT NULL,
    title        VARCHAR(191)   NOT NULL,
    cost         DECIMAL(19, 4) NULL,
    start_date   DATE           NULL,
    end_date     DATE           NULL,
    summary      TEXT           NULL,
    status       VARCHAR(12)    NOT NULL,
    progress     INT            NOT NULL DEFAULT 0,
    deleted      BIT(1)         NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_prj_milestone_project (project_id, deleted),
    CONSTRAINT fk_prj_milestone_project FOREIGN KEY (project_id) REFERENCES prj_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-PROJECT:20261122310000-5
-- Attachments: media-service file ids of the project company; name copied for display.
CREATE TABLE prj_attachment
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    project_id   BIGINT       NOT NULL,
    file_id      BIGINT       NOT NULL,
    name         VARCHAR(255) NULL,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_prj_attachment_project (project_id),
    CONSTRAINT fk_prj_attachment_project FOREIGN KEY (project_id) REFERENCES prj_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-PROJECT:20261122310000-6
-- Activity log written by the service; who = created_by (Keycloak sub) + actor_user_id.
CREATE TABLE prj_activity
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    project_id    BIGINT       NOT NULL,
    actor_user_id BIGINT       NULL,
    action        VARCHAR(40)  NOT NULL,
    detail        VARCHAR(500) NULL,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_prj_activity_project (project_id, id),
    CONSTRAINT fk_prj_activity_project FOREIGN KEY (project_id) REFERENCES prj_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-PROJECT:20261122310000-7
-- Task and bug stages per company, ordered by position; the last TASK stage is the done stage.
CREATE TABLE prj_stage
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    kind         VARCHAR(10)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    position     INT          NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_prj_stage_company (company_id, kind, deleted, position)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-PROJECT:20261122310000-8
-- Tasks: priority LOW / MEDIUM / HIGH; position orders the cards of a stage column.
CREATE TABLE prj_task
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    project_id   BIGINT       NOT NULL,
    milestone_id BIGINT       NULL,
    title        VARCHAR(191) NOT NULL,
    priority     VARCHAR(10)  NOT NULL,
    stage_id     BIGINT       NOT NULL,
    position     INT          NOT NULL DEFAULT 0,
    start_date   DATE         NULL,
    due_date     DATE         NULL,
    description  TEXT         NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_prj_task_project (project_id, deleted, stage_id, position),
    INDEX idx_prj_task_due (company_id, deleted, due_date),
    CONSTRAINT fk_prj_task_project FOREIGN KEY (project_id) REFERENCES prj_project (id),
    CONSTRAINT fk_prj_task_milestone FOREIGN KEY (milestone_id) REFERENCES prj_milestone (id),
    CONSTRAINT fk_prj_task_stage FOREIGN KEY (stage_id) REFERENCES prj_stage (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-PROJECT:20261122310000-9
-- Task assignees: IAM user ids, members of the project team.
CREATE TABLE prj_task_assignee
(
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, user_id),
    INDEX idx_prj_task_assignee_user (user_id),
    CONSTRAINT fk_prj_task_assignee_task FOREIGN KEY (task_id) REFERENCES prj_task (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-PROJECT:20261122310000-10
-- Subtasks: a checklist of a task.
CREATE TABLE prj_subtask
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    task_id      BIGINT       NOT NULL,
    name         VARCHAR(191) NOT NULL,
    done         BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_prj_subtask_task (task_id),
    CONSTRAINT fk_prj_subtask_task FOREIGN KEY (task_id) REFERENCES prj_task (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-PROJECT:20261122310000-11
-- Task comments.
CREATE TABLE prj_task_comment
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT      NOT NULL,
    task_id        BIGINT      NOT NULL,
    author_user_id BIGINT      NULL,
    body           TEXT        NOT NULL,
    deleted        BIT(1)      NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_prj_task_comment_task (task_id, deleted),
    CONSTRAINT fk_prj_task_comment_task FOREIGN KEY (task_id) REFERENCES prj_task (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-PROJECT:20261122310000-12
-- Bugs (FR-PRJ-005): one assignee; stage = status.
CREATE TABLE prj_bug
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT       NOT NULL,
    project_id       BIGINT       NOT NULL,
    title            VARCHAR(191) NOT NULL,
    priority         VARCHAR(10)  NOT NULL,
    assignee_user_id BIGINT       NULL,
    stage_id         BIGINT       NOT NULL,
    position         INT          NOT NULL DEFAULT 0,
    description      TEXT         NULL,
    deleted          BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_prj_bug_project (project_id, deleted, stage_id, position),
    CONSTRAINT fk_prj_bug_project FOREIGN KEY (project_id) REFERENCES prj_project (id),
    CONSTRAINT fk_prj_bug_stage FOREIGN KEY (stage_id) REFERENCES prj_stage (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
