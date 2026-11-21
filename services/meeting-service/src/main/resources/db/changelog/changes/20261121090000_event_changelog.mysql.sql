-- liquibase formatted sql

-- changeset ERP-MEETING:20261121090000-1
-- Event types (docs/analysis/06 9.2.2): name unique per company (checked by the service).
CREATE TABLE event_type
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT        NOT NULL,
    name         VARCHAR(150)  NOT NULL,
    description  VARCHAR(1000) NULL,
    status       VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    deleted      BIT(1)        NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_date DATETIME(6)   NULL,
    updated_by   VARCHAR(64)   NULL,
    updated_date DATETIME(6)   NULL,
    INDEX idx_event_type_company (company_id)
);

-- changeset ERP-MEETING:20261121090000-2
-- Company events (docs/analysis/06 9.2.1) with an approval status; times are optional (all-day event).
CREATE TABLE company_event
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT        NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    event_type_id BIGINT        NOT NULL,
    start_date    DATE          NOT NULL,
    end_date      DATE          NOT NULL,
    start_time    TIME          NULL,
    end_time      TIME          NULL,
    location      VARCHAR(500)  NULL,
    color         VARCHAR(7)    NOT NULL DEFAULT '#3B82F6',
    description   VARCHAR(4000) NULL,
    status        VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    decided_by    VARCHAR(64)   NULL,
    decided_at    DATETIME(6)   NULL,
    deleted       BIT(1)        NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)   NULL,
    created_date  DATETIME(6)   NULL,
    updated_by    VARCHAR(64)   NULL,
    updated_date  DATETIME(6)   NULL,
    INDEX idx_company_event_company (company_id, start_date),
    INDEX idx_company_event_type (event_type_id),
    CONSTRAINT fk_company_event_type FOREIGN KEY (event_type_id) REFERENCES event_type (id)
);

-- changeset ERP-MEETING:20261121090000-3
-- Departments of an event (employee-service ids; the name is copied so a later rename or deletion keeps the list).
CREATE TABLE company_event_department
(
    event_id      BIGINT       NOT NULL,
    department_id BIGINT       NOT NULL,
    name          VARCHAR(200) NOT NULL,
    PRIMARY KEY (event_id, department_id),
    CONSTRAINT fk_company_event_department_event FOREIGN KEY (event_id) REFERENCES company_event (id)
);
