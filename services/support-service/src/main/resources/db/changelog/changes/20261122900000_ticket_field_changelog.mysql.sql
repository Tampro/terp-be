-- liquibase formatted sql

-- changeset ERP-SUPPORT:20261122900000-1
-- Custom ticket form fields (reference SupportTicket ticket_fields: name, type, placeholder, width, order, status,
-- is_required). options_json: SELECT only, JSON array of strings.
CREATE TABLE ticket_field
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    label        VARCHAR(255) NOT NULL,
    placeholder  VARCHAR(255) NULL,
    field_type   VARCHAR(20)  NOT NULL,
    options_json TEXT         NULL,
    required     BIT(1)       NOT NULL DEFAULT 0,
    width        INT          NOT NULL DEFAULT 6,
    sort_order   INT          NOT NULL DEFAULT 0,
    active       BIT(1)       NOT NULL DEFAULT 1,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_ticket_field_company (company_id, deleted, active, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-SUPPORT:20261122900000-2
-- One value per ticket and field (reference ticket_field_values, unique record_id + field_id).
CREATE TABLE ticket_field_value
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id    BIGINT        NOT NULL,
    field_id     BIGINT        NOT NULL,
    field_value  VARCHAR(2000) NOT NULL,
    created_by   VARCHAR(64)   NULL,
    updated_by   VARCHAR(64)   NULL,
    created_date DATETIME(6)   NULL,
    updated_date DATETIME(6)   NULL,
    UNIQUE KEY uk_ticket_field_value (ticket_id, field_id),
    INDEX idx_ticket_field_value_field (field_id),
    CONSTRAINT fk_ticket_field_value_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id),
    CONSTRAINT fk_ticket_field_value_field FOREIGN KEY (field_id) REFERENCES ticket_field (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
