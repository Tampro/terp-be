-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120110000-1
-- Minimal organization (docs/analysis/06 §6.3.1–6.3.3): branch > department > designation, per company.
CREATE TABLE branch
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(150) NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_branch_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE department
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    branch_id    BIGINT       NOT NULL,
    name         VARCHAR(150) NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_department_company (company_id),
    CONSTRAINT fk_department_branch FOREIGN KEY (branch_id) REFERENCES branch (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE designation
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    department_id BIGINT       NOT NULL,
    name          VARCHAR(150) NOT NULL,
    deleted       BIT(1)       NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    updated_by    VARCHAR(64)  NULL,
    created_date  DATETIME(6)  NULL,
    updated_date  DATETIME(6)  NULL,
    INDEX idx_designation_company (company_id),
    CONSTRAINT fk_designation_department FOREIGN KEY (department_id) REFERENCES department (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-EMPLOYEE:20261120110000-2
-- company_id NULL = system type shared by every company (read-only).
CREATE TABLE document_type
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NULL,
    name         VARCHAR(150) NOT NULL,
    is_default   BIT(1)       NOT NULL DEFAULT 0,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_document_type_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-EMPLOYEE:20261120110000-3
-- company_id is the IAM company id (another service's database: no foreign key).
CREATE TABLE employee
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id             BIGINT         NOT NULL,
    user_id                BIGINT         NULL,
    login_enabled          BIT(1)         NOT NULL DEFAULT 0,
    employee_no            VARCHAR(20)    NOT NULL,
    employee_code          VARCHAR(50)    NULL,
    full_name              VARCHAR(255)   NOT NULL,
    email                  VARCHAR(255)   NOT NULL,
    phone                  VARCHAR(30)    NULL,
    date_of_birth          DATE           NULL,
    gender                 VARCHAR(10)    NULL,
    profile_media_id       BIGINT         NULL,
    branch_id              BIGINT         NULL,
    department_id          BIGINT         NULL,
    designation_id         BIGINT         NULL,
    date_of_joining        DATE           NOT NULL,
    employment_type        VARCHAR(20)    NOT NULL,
    status                 VARCHAR(20)    NOT NULL,
    shift_id               BIGINT         NULL,
    attendance_policy_id   BIGINT         NULL,
    address_line1          VARCHAR(255)   NULL,
    address_line2          VARCHAR(255)   NULL,
    city                   VARCHAR(100)   NULL,
    state                  VARCHAR(100)   NULL,
    country                VARCHAR(100)   NULL,
    postal_code            VARCHAR(20)    NULL,
    emergency_name         VARCHAR(255)   NULL,
    emergency_relationship VARCHAR(50)    NULL,
    emergency_phone        VARCHAR(30)    NULL,
    bank_name              VARCHAR(150)   NULL,
    account_holder_name    VARCHAR(255)   NULL,
    account_number_enc     VARCHAR(255)   NULL,
    bank_identifier_code   VARCHAR(20)    NULL,
    bank_branch            VARCHAR(150)   NULL,
    tax_payer_id_enc       VARCHAR(255)   NULL,
    base_salary            DECIMAL(15, 2) NULL,
    deleted                BIT(1)         NOT NULL DEFAULT 0,
    created_by             VARCHAR(64)    NULL,
    updated_by             VARCHAR(64)    NULL,
    created_date           DATETIME(6)    NULL,
    updated_date           DATETIME(6)    NULL,
    -- Issued numbers are never reused, deleted rows included.
    CONSTRAINT uk_employee_no UNIQUE (company_id, employee_no),
    INDEX idx_employee_company_status (company_id, deleted, status),
    INDEX idx_employee_company_email (company_id, email),
    CONSTRAINT fk_employee_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT fk_employee_designation FOREIGN KEY (designation_id) REFERENCES designation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE employee_sequence
(
    company_id BIGINT PRIMARY KEY,
    next_value BIGINT NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-EMPLOYEE:20261120110000-4
CREATE TABLE employee_document
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT      NOT NULL,
    employee_id      BIGINT      NOT NULL,
    document_type_id BIGINT      NOT NULL,
    media_file_id    BIGINT      NOT NULL,
    expiry_date      DATE        NULL,
    verified         BIT(1)      NOT NULL DEFAULT 0,
    verified_by      VARCHAR(64) NULL,
    verified_at      DATETIME(6) NULL,
    created_by       VARCHAR(64) NULL,
    updated_by       VARCHAR(64) NULL,
    created_date     DATETIME(6) NULL,
    updated_date     DATETIME(6) NULL,
    CONSTRAINT fk_employee_document_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_employee_document_type FOREIGN KEY (document_type_id) REFERENCES document_type (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
