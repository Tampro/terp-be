-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120440000-1
-- Training sessions (docs/analysis/06 7.4.2). Times are local wall-clock times of the company. A recurring session
-- is saved as copies sharing series_id (PO, 2026-11-20); each copy is edited on its own.
CREATE TABLE training_session
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    program_id    BIGINT       NOT NULL,
    name          VARCHAR(200) NOT NULL,
    start_at      DATETIME     NOT NULL,
    end_at        DATETIME     NOT NULL,
    location_type VARCHAR(12)  NOT NULL,
    location      VARCHAR(300) NULL,
    meeting_link  VARCHAR(500) NULL,
    status        VARCHAR(12)  NOT NULL DEFAULT 'SCHEDULED',
    notes         TEXT         NULL,
    series_id     VARCHAR(36)  NULL,
    deleted       BIT(1)       NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    created_date  DATETIME(6)  NULL,
    updated_by    VARCHAR(64)  NULL,
    updated_date  DATETIME(6)  NULL,
    INDEX idx_training_session_company (company_id, start_at),
    INDEX idx_training_session_program (program_id),
    CONSTRAINT fk_training_session_program FOREIGN KEY (program_id) REFERENCES training_program (id)
);

-- changeset ERP-EMPLOYEE:20261120440000-2
CREATE TABLE training_session_trainer
(
    session_id  BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    PRIMARY KEY (session_id, employee_id),
    CONSTRAINT fk_training_session_trainer_session FOREIGN KEY (session_id) REFERENCES training_session (id)
);

-- changeset ERP-EMPLOYEE:20261120440000-3
-- Attendance marked for the program's trainees; a trainee without a row is not marked yet.
CREATE TABLE training_attendance
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id   BIGINT      NOT NULL,
    employee_id  BIGINT      NOT NULL,
    status       VARCHAR(10) NOT NULL,
    notes        VARCHAR(1000) NULL,
    created_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL,
    UNIQUE KEY uk_training_attendance (session_id, employee_id),
    CONSTRAINT fk_training_attendance_session FOREIGN KEY (session_id) REFERENCES training_session (id)
);
