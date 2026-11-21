-- liquibase formatted sql

-- changeset ERP-IAM:20261121320000-1
-- Platform helpdesk (ERP manual 3.3): categories belong to the platform, tickets to the company that opened them.
CREATE TABLE helpdesk_category
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(500) NULL,
    color        VARCHAR(7)   NOT NULL DEFAULT '#3B82F6',
    active       BIT(1)       NOT NULL DEFAULT 1,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261121320000-2
CREATE TABLE helpdesk_ticket
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    ticket_number VARCHAR(20)  NULL,
    title         VARCHAR(255) NOT NULL,
    description   TEXT         NOT NULL,
    category_id   BIGINT       NULL,
    status        VARCHAR(20)  NOT NULL,
    priority      VARCHAR(20)  NOT NULL,
    resolved_at   DATETIME(6)  NULL,
    deleted       BIT(1)       NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    updated_by    VARCHAR(64)  NULL,
    created_date  DATETIME(6)  NULL,
    updated_date  DATETIME(6)  NULL,
    CONSTRAINT uk_helpdesk_ticket_number UNIQUE (ticket_number),
    INDEX idx_helpdesk_ticket_company (company_id, status),
    INDEX idx_helpdesk_ticket_created (created_date),
    CONSTRAINT fk_helpdesk_ticket_company FOREIGN KEY (company_id) REFERENCES company (id),
    CONSTRAINT fk_helpdesk_ticket_category FOREIGN KEY (category_id) REFERENCES helpdesk_category (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261121320000-3
CREATE TABLE helpdesk_reply
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id     BIGINT       NOT NULL,
    company_id    BIGINT       NOT NULL,
    message       TEXT         NOT NULL,
    internal      BIT(1)       NOT NULL DEFAULT 0,
    from_platform BIT(1)       NOT NULL DEFAULT 0,
    author_name   VARCHAR(255) NOT NULL,
    deleted       BIT(1)       NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    updated_by    VARCHAR(64)  NULL,
    created_date  DATETIME(6)  NULL,
    updated_date  DATETIME(6)  NULL,
    INDEX idx_helpdesk_reply_ticket (ticket_id),
    CONSTRAINT fk_helpdesk_reply_ticket FOREIGN KEY (ticket_id) REFERENCES helpdesk_ticket (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261121320000-4
-- Attachment = a file of media-service (id only, no cross-database key).
CREATE TABLE helpdesk_reply_attachment
(
    reply_id      BIGINT NOT NULL,
    position      INT    NOT NULL,
    media_file_id BIGINT NOT NULL,
    PRIMARY KEY (reply_id, position),
    CONSTRAINT fk_helpdesk_attachment_reply FOREIGN KEY (reply_id) REFERENCES helpdesk_reply (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
