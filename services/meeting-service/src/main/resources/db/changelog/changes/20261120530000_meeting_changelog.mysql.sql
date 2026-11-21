-- liquibase formatted sql

-- changeset ERP-MEETING:20261120530000-1
-- Meetings (docs/analysis/06 9.1.1). A recurring meeting is stored as one row per occurrence sharing series_id (PO,
-- 2026-11-20); the organizer lives in employee-service, name / email copied for the record.
CREATE TABLE meeting
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT        NOT NULL,
    series_id           BIGINT        NULL,
    title               VARCHAR(200)  NOT NULL,
    description         VARCHAR(2000) NULL,
    agenda              VARCHAR(4000) NULL,
    meeting_type_id     BIGINT        NULL,
    room_id             BIGINT        NULL,
    meeting_date        DATE          NOT NULL,
    start_time          TIME          NOT NULL,
    end_time            TIME          NOT NULL,
    organizer_id        BIGINT        NOT NULL,
    organizer_name      VARCHAR(200)  NULL,
    organizer_email     VARCHAR(255)  NULL,
    recurrence          VARCHAR(10)   NOT NULL DEFAULT 'NONE',
    recurrence_end_date DATE          NULL,
    status              VARCHAR(12)   NOT NULL DEFAULT 'SCHEDULED',
    deleted             BIT(1)        NOT NULL DEFAULT 0,
    created_by          VARCHAR(64)   NULL,
    created_date        DATETIME(6)   NULL,
    updated_by          VARCHAR(64)   NULL,
    updated_date        DATETIME(6)   NULL,
    INDEX idx_meeting_company_date (company_id, meeting_date),
    INDEX idx_meeting_room_date (room_id, meeting_date),
    INDEX idx_meeting_type (meeting_type_id),
    CONSTRAINT fk_meeting_type FOREIGN KEY (meeting_type_id) REFERENCES meeting_type (id),
    CONSTRAINT fk_meeting_room FOREIGN KEY (room_id) REFERENCES meeting_room (id)
);

-- changeset ERP-MEETING:20261120530000-2
-- Attendees of a meeting (employee ids of employee-service).
CREATE TABLE meeting_attendee
(
    meeting_id  BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    PRIMARY KEY (meeting_id, employee_id),
    INDEX idx_meeting_attendee_employee (employee_id),
    CONSTRAINT fk_meeting_attendee_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id)
);
