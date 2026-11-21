-- liquibase formatted sql

-- changeset ERP-MEETING:20261121110000-1
-- Zoom meetings (docs/analysis/06 9.3), mirrored from Zoom: zoom_meeting_id / join_url as Zoom returned them; the
-- start URL carries the host's token, so it is stored AES-256-GCM encrypted (MEETING_ENC_KEY).
CREATE TABLE zoom_meeting
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT        NOT NULL,
    zoom_meeting_id  BIGINT        NOT NULL,
    topic            VARCHAR(200)  NOT NULL,
    agenda           VARCHAR(2000) NULL,
    start_date       DATE          NOT NULL,
    start_time       TIME          NOT NULL,
    duration_minutes INT           NOT NULL,
    passcode         VARCHAR(10)   NOT NULL,
    join_url         VARCHAR(1000) NOT NULL,
    start_url_cipher VARCHAR(4000) NOT NULL,
    status           VARCHAR(10)   NOT NULL DEFAULT 'SCHEDULED',
    deleted          BIT(1)        NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)   NULL,
    created_date     DATETIME(6)   NULL,
    updated_by       VARCHAR(64)   NULL,
    updated_date     DATETIME(6)   NULL,
    INDEX idx_zoom_meeting_company_date (company_id, start_date)
);

-- changeset ERP-MEETING:20261121110000-2
-- Invited employees (employee-service ids).
CREATE TABLE zoom_meeting_invitee
(
    zoom_meeting_id BIGINT NOT NULL,
    employee_id     BIGINT NOT NULL,
    PRIMARY KEY (zoom_meeting_id, employee_id),
    CONSTRAINT fk_zoom_meeting_invitee_meeting FOREIGN KEY (zoom_meeting_id) REFERENCES zoom_meeting (id)
);
