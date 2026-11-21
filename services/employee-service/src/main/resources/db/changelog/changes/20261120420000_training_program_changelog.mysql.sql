-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120420000-1
-- Training types (docs/analysis/06 7.4.5): optional branch; the departments assigned to the type (Assign Departments)
-- belong to that branch when one is set.
CREATE TABLE training_type
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    name         VARCHAR(150) NOT NULL,
    description  VARCHAR(1000) NULL,
    branch_id    BIGINT       NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_training_type_company (company_id)
);

-- changeset ERP-EMPLOYEE:20261120420000-2
CREATE TABLE training_type_department
(
    training_type_id BIGINT NOT NULL,
    department_id    BIGINT NOT NULL,
    PRIMARY KEY (training_type_id, department_id),
    INDEX idx_training_type_department_dept (department_id),
    CONSTRAINT fk_training_type_department_type FOREIGN KEY (training_type_id) REFERENCES training_type (id)
);

-- changeset ERP-EMPLOYEE:20261120420000-3
-- Training programs (7.4.3). Materials are media library files (training_program_material).
CREATE TABLE training_program
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT         NOT NULL,
    training_type_id BIGINT         NOT NULL,
    name             VARCHAR(200)   NOT NULL,
    description      TEXT           NULL,
    duration_hours   DECIMAL(7, 1)  NULL,
    cost             DECIMAL(15, 2) NULL,
    capacity         INT            NULL,
    status           VARCHAR(12)    NOT NULL DEFAULT 'DRAFT',
    prerequisites    TEXT           NULL,
    mandatory        BIT(1)         NOT NULL DEFAULT 0,
    self_enrollment  BIT(1)         NOT NULL DEFAULT 0,
    deleted          BIT(1)         NOT NULL DEFAULT 0,
    created_by       VARCHAR(64)    NULL,
    created_date     DATETIME(6)    NULL,
    updated_by       VARCHAR(64)    NULL,
    updated_date     DATETIME(6)    NULL,
    INDEX idx_training_program_company (company_id, training_type_id),
    CONSTRAINT fk_training_program_type FOREIGN KEY (training_type_id) REFERENCES training_type (id)
);

-- changeset ERP-EMPLOYEE:20261120420000-4
CREATE TABLE training_program_material
(
    program_id     BIGINT NOT NULL,
    media_id       BIGINT NOT NULL,
    PRIMARY KEY (program_id, media_id),
    CONSTRAINT fk_training_program_material_program FOREIGN KEY (program_id) REFERENCES training_program (id)
);
