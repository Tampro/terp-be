-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120170000-1
-- Announcement categories (docs/analysis/06 §6.3.5): company_id null = system category, read-only.
CREATE TABLE announcement_category
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NULL,
    name         VARCHAR(100) NOT NULL,
    color        CHAR(7)      NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_announcement_category_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-EMPLOYEE:20261120170000-2
-- content is sanitized HTML (rich-text editor). company_wide = every employee; otherwise the audience is the
-- employees of ANY target branch OR ANY target department (union).
CREATE TABLE announcement
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT       NOT NULL,
    title             VARCHAR(200) NOT NULL,
    category_id       BIGINT       NOT NULL,
    short_description VARCHAR(500) NULL,
    content           MEDIUMTEXT   NOT NULL,
    start_date        DATE         NOT NULL,
    end_date          DATE         NOT NULL,
    featured          BIT(1)       NOT NULL DEFAULT 0,
    is_high_priority  BIT(1)       NOT NULL DEFAULT 0,
    company_wide      BIT(1)       NOT NULL DEFAULT 0,
    deleted           BIT(1)       NOT NULL DEFAULT 0,
    created_by        VARCHAR(64)  NULL,
    updated_by        VARCHAR(64)  NULL,
    created_date      DATETIME(6)  NULL,
    updated_date      DATETIME(6)  NULL,
    INDEX idx_announcement_company_dates (company_id, start_date, end_date),
    CONSTRAINT fk_announcement_category FOREIGN KEY (category_id) REFERENCES announcement_category (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-EMPLOYEE:20261120170000-3
CREATE TABLE announcement_branch
(
    announcement_id BIGINT NOT NULL,
    branch_id       BIGINT NOT NULL,
    PRIMARY KEY (announcement_id, branch_id),
    INDEX idx_announcement_branch_branch (branch_id),
    CONSTRAINT fk_announcement_branch_announcement FOREIGN KEY (announcement_id) REFERENCES announcement (id),
    CONSTRAINT fk_announcement_branch_branch FOREIGN KEY (branch_id) REFERENCES branch (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-EMPLOYEE:20261120170000-4
CREATE TABLE announcement_department
(
    announcement_id BIGINT NOT NULL,
    department_id   BIGINT NOT NULL,
    PRIMARY KEY (announcement_id, department_id),
    INDEX idx_announcement_department_department (department_id),
    CONSTRAINT fk_announcement_department_announcement FOREIGN KEY (announcement_id) REFERENCES announcement (id),
    CONSTRAINT fk_announcement_department_department FOREIGN KEY (department_id) REFERENCES department (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-EMPLOYEE:20261120170000-5
-- Attachments are media-service files (other database: no foreign key); name/type/size are kept so lists show them
-- without calling media-service.
CREATE TABLE announcement_attachment
(
    announcement_id BIGINT       NOT NULL,
    position        INT          NOT NULL,
    media_file_id   BIGINT       NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    mime_type       VARCHAR(127) NULL,
    size_bytes      BIGINT       NOT NULL,
    PRIMARY KEY (announcement_id, position),
    CONSTRAINT fk_announcement_attachment_announcement FOREIGN KEY (announcement_id) REFERENCES announcement (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-EMPLOYEE:20261120170000-6
-- One row per employee who opened the announcement: views are unique viewers.
CREATE TABLE announcement_view
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    announcement_id BIGINT      NOT NULL,
    employee_id     BIGINT      NOT NULL,
    viewed_at       DATETIME(6) NOT NULL,
    UNIQUE KEY uk_announcement_view (announcement_id, employee_id),
    INDEX idx_announcement_view_employee (employee_id),
    CONSTRAINT fk_announcement_view_announcement FOREIGN KEY (announcement_id) REFERENCES announcement (id),
    CONSTRAINT fk_announcement_view_employee FOREIGN KEY (employee_id) REFERENCES employee (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
