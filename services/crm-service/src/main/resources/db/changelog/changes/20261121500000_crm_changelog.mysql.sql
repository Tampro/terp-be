-- liquibase formatted sql

-- changeset ERP-CRM:20261121500000-1
-- CRM setup (ERP manual 7.1): pipelines; lead and deal stages per pipeline, ordered; labels per pipeline; sources.
CREATE TABLE crm_pipeline
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_crm_pipeline_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-CRM:20261121500000-2
CREATE TABLE crm_stage
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    pipeline_id  BIGINT       NOT NULL,
    kind         VARCHAR(10)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    position     INT          NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_crm_stage_pipeline (pipeline_id, kind, position),
    CONSTRAINT fk_crm_stage_pipeline FOREIGN KEY (pipeline_id) REFERENCES crm_pipeline (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-CRM:20261121500000-3
CREATE TABLE crm_label
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    pipeline_id  BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    color        VARCHAR(7)   NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_crm_label_pipeline (pipeline_id),
    CONSTRAINT fk_crm_label_pipeline FOREIGN KEY (pipeline_id) REFERENCES crm_pipeline (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-CRM:20261121500000-4
CREATE TABLE crm_source
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_crm_source_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-CRM:20261121500000-5
-- Leads (ERP manual 7.2). owner_user_id = the assigned user (IAM account id); converted_deal_id set once.
CREATE TABLE crm_lead
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT       NOT NULL,
    pipeline_id       BIGINT       NOT NULL,
    stage_id          BIGINT       NOT NULL,
    name              VARCHAR(255) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    subject           VARCHAR(255) NOT NULL,
    phone             VARCHAR(30)  NULL,
    follow_up_date    DATE         NULL,
    owner_user_id     BIGINT       NOT NULL,
    notes             TEXT         NULL,
    position          INT          NOT NULL DEFAULT 0,
    converted_deal_id BIGINT       NULL,
    deleted           BIT(1)       NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)  NULL,
    updated_by        VARCHAR(64)  NULL,
    created_date      DATETIME(6)  NULL,
    updated_date      DATETIME(6)  NULL,
    INDEX idx_crm_lead_board (company_id, pipeline_id, stage_id, position),
    CONSTRAINT fk_crm_lead_pipeline FOREIGN KEY (pipeline_id) REFERENCES crm_pipeline (id),
    CONSTRAINT fk_crm_lead_stage FOREIGN KEY (stage_id) REFERENCES crm_stage (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-CRM:20261121500000-6
-- Deals (ERP manual 7.3). status ACTIVE / WON / LOSS; from_lead_id when made by a conversion.
CREATE TABLE crm_deal
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    pipeline_id  BIGINT         NOT NULL,
    stage_id     BIGINT         NOT NULL,
    name         VARCHAR(255)   NOT NULL,
    price        DECIMAL(19, 4) NOT NULL DEFAULT 0,
    phone        VARCHAR(30)    NULL,
    status       VARCHAR(10)    NOT NULL,
    notes        TEXT           NULL,
    position     INT            NOT NULL DEFAULT 0,
    from_lead_id BIGINT         NULL,
    deleted      BIT(1)         NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)    NULL,
    updated_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_date DATETIME(6)    NULL,
    INDEX idx_crm_deal_board (company_id, pipeline_id, stage_id, position),
    CONSTRAINT fk_crm_deal_pipeline FOREIGN KEY (pipeline_id) REFERENCES crm_pipeline (id),
    CONSTRAINT fk_crm_deal_stage FOREIGN KEY (stage_id) REFERENCES crm_stage (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-CRM:20261121500000-7
-- Links of a lead or deal: MEMBER (IAM user), CLIENT (accounting customer, deals), LABEL, SOURCE, PRODUCT
-- (inventory item). ref_name is a copy for display. Ids of other services: no cross-database key.
CREATE TABLE crm_link
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    subject_type VARCHAR(10)  NOT NULL,
    subject_id   BIGINT       NOT NULL,
    link_type    VARCHAR(10)  NOT NULL,
    ref_id       BIGINT       NOT NULL,
    ref_name     VARCHAR(255) NULL,
    CONSTRAINT uk_crm_link UNIQUE (subject_type, subject_id, link_type, ref_id),
    INDEX idx_crm_link_ref (company_id, link_type, ref_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-CRM:20261121500000-8
-- Activity of a lead or deal (ERP manual 7.2 detail tabs): tasks, calls, discussions, emails (logged, not sent),
-- files (media-service ids) and the automatic activity log. One table per kind, shared by leads and deals.
CREATE TABLE crm_task
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    subject_type VARCHAR(10)  NOT NULL,
    subject_id   BIGINT       NOT NULL,
    name         VARCHAR(255) NOT NULL,
    task_date    DATE         NOT NULL,
    task_time    TIME         NULL,
    priority     VARCHAR(10)  NOT NULL,
    status       VARCHAR(10)  NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_crm_task_subject (subject_type, subject_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE crm_call
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT       NOT NULL,
    subject_type     VARCHAR(10)  NOT NULL,
    subject_id       BIGINT       NOT NULL,
    subject          VARCHAR(255) NOT NULL,
    call_type        VARCHAR(10)  NOT NULL,
    duration         VARCHAR(20)  NULL,
    assignee_user_id BIGINT       NULL,
    description      TEXT         NULL,
    call_result      TEXT         NULL,
    deleted          BIT(1)       NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)  NULL,
    updated_by       VARCHAR(64)  NULL,
    created_date     DATETIME(6)  NULL,
    updated_date     DATETIME(6)  NULL,
    INDEX idx_crm_call_subject (subject_type, subject_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE crm_entry
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    subject_type VARCHAR(10)  NOT NULL,
    subject_id   BIGINT       NOT NULL,
    entry_type   VARCHAR(12)  NOT NULL,
    title        VARCHAR(255) NULL,
    body         TEXT         NULL,
    email_to     VARCHAR(255) NULL,
    media_file_id BIGINT      NULL,
    author_name  VARCHAR(255) NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_crm_entry_subject (subject_type, subject_id, entry_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
