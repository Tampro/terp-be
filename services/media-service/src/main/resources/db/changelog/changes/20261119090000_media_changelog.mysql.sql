-- liquibase formatted sql

-- changeset ERP-MEDIA:20261119090000-1
-- company_id NULL = platform (super admin) files. No FK: companies live in the IAM database.
CREATE TABLE media_folder
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NULL,
    name         VARCHAR(100) NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(64)  NULL,
    updated_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_date DATETIME(6)  NULL,
    INDEX idx_media_folder_company (company_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-MEDIA:20261119090000-2
CREATE TABLE media_file
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT       NULL,
    folder_id     BIGINT       NULL,
    original_name VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    mime_type     VARCHAR(100) NOT NULL,
    extension     VARCHAR(10)  NOT NULL,
    kind          VARCHAR(20)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    storage_key   VARCHAR(200) NOT NULL,
    created_by    VARCHAR(64)  NULL,
    updated_by    VARCHAR(64)  NULL,
    created_date  DATETIME(6)  NULL,
    updated_date  DATETIME(6)  NULL,
    CONSTRAINT uk_media_file_storage_key UNIQUE (storage_key),
    INDEX idx_media_file_company (company_id, folder_id),
    CONSTRAINT fk_media_file_folder FOREIGN KEY (folder_id) REFERENCES media_folder (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
