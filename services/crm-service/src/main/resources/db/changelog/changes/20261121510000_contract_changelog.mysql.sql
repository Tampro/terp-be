-- liquibase formatted sql

-- changeset ERP-CRM:20261121510000-1
-- Contract types (ERP manual 7.5). Name uniqueness among not deleted rows is checked by the service.
CREATE TABLE contract_type
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(150) NOT NULL,
    active       BIT(1)       NOT NULL DEFAULT 1,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_contract_type_company (company_id, deleted, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-CRM:20261121510000-2
-- Numbering per company: prefix + 4 digits (CON0001). Locked row by row; next_number only grows (never reused).
CREATE TABLE contract_setting
(
    company_id   BIGINT      NOT NULL PRIMARY KEY,
    prefix       VARCHAR(10) NOT NULL DEFAULT 'CON',
    next_number  INT         NOT NULL DEFAULT 1,
    updated_by   VARCHAR(64) NULL,
    updated_date DATETIME(6) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-CRM:20261121510000-3
-- customer_id is an accounting-service id (no cross-database key); customer_name is a snapshot.
CREATE TABLE contract
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT         NOT NULL,
    contract_number  VARCHAR(20)    NOT NULL,
    subject          VARCHAR(255)   NOT NULL,
    customer_id      BIGINT         NOT NULL,
    customer_name    VARCHAR(255)   NOT NULL,
    contract_type_id BIGINT         NOT NULL,
    value            DECIMAL(19, 4) NULL,
    start_date       DATE           NOT NULL,
    end_date         DATE           NOT NULL,
    status           VARCHAR(12)    NOT NULL DEFAULT 'PENDING',
    description      TEXT           NULL,
    deleted          BIT(1)         NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)    NULL,
    updated_by       VARCHAR(64)    NULL,
    created_date     DATETIME(6)    NULL,
    updated_date     DATETIME(6)    NULL,
    CONSTRAINT uk_contract_number UNIQUE (company_id, contract_number),
    CONSTRAINT fk_contract_type FOREIGN KEY (contract_type_id) REFERENCES contract_type (id),
    INDEX idx_contract_list (company_id, deleted, status, start_date),
    INDEX idx_contract_customer (company_id, customer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-CRM:20261121510000-4
CREATE TABLE contract_renewal
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    contract_id  BIGINT         NOT NULL,
    start_date   DATE           NOT NULL,
    end_date     DATE           NOT NULL,
    value        DECIMAL(19, 4) NOT NULL,
    status       VARCHAR(12)    NOT NULL DEFAULT 'PENDING',
    notes        TEXT           NULL,
    deleted      BIT(1)         NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)    NULL,
    updated_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_date DATETIME(6)    NULL,
    CONSTRAINT fk_contract_renewal_contract FOREIGN KEY (contract_id) REFERENCES contract (id),
    INDEX idx_contract_renewal_contract (contract_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-CRM:20261121510000-5
CREATE TABLE contract_comment
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    contract_id  BIGINT       NOT NULL,
    message      TEXT         NOT NULL,
    author_name  VARCHAR(150) NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    CONSTRAINT fk_contract_comment_contract FOREIGN KEY (contract_id) REFERENCES contract (id),
    INDEX idx_contract_comment_contract (contract_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-CRM:20261121510000-6
CREATE TABLE contract_note
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT      NOT NULL,
    contract_id  BIGINT      NOT NULL,
    note         TEXT        NOT NULL,
    deleted      BIT(1)      NOT NULL DEFAULT 0,
    created_by   VARCHAR(64) NULL,
    updated_by   VARCHAR(64) NULL,
    created_date DATETIME(6) NULL,
    updated_date DATETIME(6) NULL,
    CONSTRAINT fk_contract_note_contract FOREIGN KEY (contract_id) REFERENCES contract (id),
    INDEX idx_contract_note_contract (contract_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-CRM:20261121510000-7
-- media_file_id is a media-service id of the contract's company; file_name is a snapshot.
CREATE TABLE contract_attachment
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    contract_id   BIGINT       NOT NULL,
    media_file_id BIGINT       NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    deleted       BIT(1)       NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    updated_by    VARCHAR(64)  NULL,
    created_date  DATETIME(6)  NULL,
    updated_date  DATETIME(6)  NULL,
    CONSTRAINT fk_contract_attachment_contract FOREIGN KEY (contract_id) REFERENCES contract (id),
    INDEX idx_contract_attachment_contract (contract_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
