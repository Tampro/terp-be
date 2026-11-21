-- liquibase formatted sql

-- changeset ERP-MEETING:20261120520000-1
-- Meeting types (docs/analysis/06 9.1.3): name unique per company (checked by the service).
CREATE TABLE meeting_type
(
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id               BIGINT        NOT NULL,
    name                     VARCHAR(150)  NOT NULL,
    description              VARCHAR(1000) NULL,
    color                    VARCHAR(7)    NOT NULL DEFAULT '#3B82F6',
    default_duration_minutes INT           NOT NULL DEFAULT 60,
    status                   VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    deleted                  BIT(1)        NOT NULL DEFAULT 0,
    created_by               VARCHAR(64)   NULL,
    created_date             DATETIME(6)   NULL,
    updated_by               VARCHAR(64)   NULL,
    updated_date             DATETIME(6)   NULL,
    INDEX idx_meeting_type_company (company_id)
);

-- changeset ERP-MEETING:20261120520000-2
-- Meeting rooms (docs/analysis/06 9.1.4): physical (address) or virtual (join link); name unique per company.
CREATE TABLE meeting_room
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT        NOT NULL,
    name         VARCHAR(150)  NOT NULL,
    description  VARCHAR(1000) NULL,
    room_type    VARCHAR(10)   NOT NULL,
    location     VARCHAR(500)  NULL,
    capacity     INT           NOT NULL,
    status       VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    deleted      BIT(1)        NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)   NULL,
    created_date DATETIME(6)   NULL,
    updated_by   VARCHAR(64)   NULL,
    updated_date DATETIME(6)   NULL,
    INDEX idx_meeting_room_company (company_id)
);

-- changeset ERP-MEETING:20261120520000-3
-- Equipment of a room, one item per row (the Equipment column counts them).
CREATE TABLE meeting_room_equipment
(
    room_id  BIGINT       NOT NULL,
    position INT          NOT NULL,
    name     VARCHAR(100) NOT NULL,
    PRIMARY KEY (room_id, position),
    CONSTRAINT fk_meeting_room_equipment_room FOREIGN KEY (room_id) REFERENCES meeting_room (id)
);
