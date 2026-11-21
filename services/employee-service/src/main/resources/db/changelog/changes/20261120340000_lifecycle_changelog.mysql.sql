-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120340000-1
-- Awards (docs/analysis/06 §7.2.1). certificate / photo are media-library file ids of the company.
CREATE TABLE employee_award
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT        NOT NULL,
    employee_id          BIGINT        NOT NULL,
    award_type_id        BIGINT        NOT NULL,
    award_date           DATE          NOT NULL,
    gift                 VARCHAR(255)  NULL,
    description          VARCHAR(2000) NULL,
    certificate_media_id BIGINT        NULL,
    photo_media_id       BIGINT        NULL,
    deleted              BIT(1)        NOT NULL DEFAULT 0,
    created_by           VARCHAR(64)   NULL,
    created_date         DATETIME(6)   NULL,
    updated_by           VARCHAR(64)   NULL,
    updated_date         DATETIME(6)   NULL,
    CONSTRAINT fk_award_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_award_type FOREIGN KEY (award_type_id) REFERENCES award_type (id),
    INDEX idx_award_company_date (company_id, award_date)
);

-- changeset ERP-EMPLOYEE:20261120340000-2
-- Promotions (§7.2.2). previous_designation_* is the employee's designation when recorded (name kept as text: the
-- spec lets the admin type it). APPROVED changes the employee's designation at once (PO, 2026-11-20).
CREATE TABLE employee_promotion
(
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT        NOT NULL,
    employee_id             BIGINT        NOT NULL,
    previous_designation_id BIGINT        NULL,
    previous_designation    VARCHAR(150)  NULL,
    new_designation_id      BIGINT        NOT NULL,
    promotion_date          DATE          NOT NULL,
    effective_date          DATE          NOT NULL,
    reason                  VARCHAR(2000) NULL,
    document_media_id       BIGINT        NULL,
    status                  VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    decided_at              DATETIME(6)   NULL,
    deleted                 BIT(1)        NOT NULL DEFAULT 0,
    created_by              VARCHAR(64)   NULL,
    created_date            DATETIME(6)   NULL,
    updated_by              VARCHAR(64)   NULL,
    updated_date            DATETIME(6)   NULL,
    CONSTRAINT fk_promotion_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_promotion_designation FOREIGN KEY (new_designation_id) REFERENCES designation (id),
    INDEX idx_promotion_company (company_id, status)
);

-- changeset ERP-EMPLOYEE:20261120340000-3
-- Transfers (§7.2.3). from_* is the placement when recorded, refreshed at approval; APPROVED moves the employee.
CREATE TABLE employee_transfer
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT        NOT NULL,
    employee_id         BIGINT        NOT NULL,
    from_branch_id      BIGINT        NULL,
    from_department_id  BIGINT        NULL,
    from_designation_id BIGINT        NULL,
    to_branch_id        BIGINT        NULL,
    to_department_id    BIGINT        NULL,
    to_designation_id   BIGINT        NULL,
    transfer_date       DATE          NOT NULL,
    effective_date      DATE          NOT NULL,
    reason              VARCHAR(2000) NULL,
    document_media_id   BIGINT        NULL,
    status              VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    decided_at          DATETIME(6)   NULL,
    deleted             BIT(1)        NOT NULL DEFAULT 0,
    created_by          VARCHAR(64)   NULL,
    created_date        DATETIME(6)   NULL,
    updated_by          VARCHAR(64)   NULL,
    updated_date        DATETIME(6)   NULL,
    CONSTRAINT fk_transfer_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    INDEX idx_transfer_company (company_id, status)
);
