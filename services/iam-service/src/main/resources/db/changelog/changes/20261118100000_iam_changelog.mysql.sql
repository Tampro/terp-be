-- liquibase formatted sql

-- changeset ERP-IAM:20261118100000-1
CREATE TABLE company
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_company_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261118100000-2
CREATE TABLE permission
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(100) NOT NULL,
    module      VARCHAR(50)  NOT NULL,
    description VARCHAR(255) NULL,
    CONSTRAINT uk_permission_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261118100000-3
CREATE TABLE role
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    code         VARCHAR(50)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(255) NULL,
    system_role  BIT(1)       NOT NULL DEFAULT 0,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_role_company (company_id, code),
    CONSTRAINT fk_role_company FOREIGN KEY (company_id) REFERENCES company (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261118100000-4
CREATE TABLE role_permission
(
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permission (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261118100000-5
-- keycloak_id is unique even across soft-deleted rows: one Keycloak identity maps to at most one ERP account.
CREATE TABLE user_account
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    keycloak_id  VARCHAR(64)  NOT NULL,
    company_id   BIGINT       NOT NULL,
    username     VARCHAR(100) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    full_name    VARCHAR(255) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    CONSTRAINT uk_user_keycloak UNIQUE (keycloak_id),
    INDEX idx_user_company (company_id),
    INDEX idx_user_username (username),
    CONSTRAINT fk_user_company FOREIGN KEY (company_id) REFERENCES company (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261118100000-6
CREATE TABLE user_role
(
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES role (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
