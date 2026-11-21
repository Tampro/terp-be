-- liquibase formatted sql

-- changeset ERP-INVENTORY:20261122100000-1
-- Main image of an item: a media-service file id (no FK: another service's database).
ALTER TABLE item ADD COLUMN main_image_id BIGINT NULL AFTER description;
-- rollback ALTER TABLE item DROP COLUMN main_image_id;

-- changeset ERP-INVENTORY:20261122100000-2
-- Additional images of an item, in display order; media_file_id points to media-service (no FK).
CREATE TABLE item_image (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    company_id    BIGINT       NOT NULL,
    item_id       BIGINT       NOT NULL,
    media_file_id BIGINT       NOT NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)  NULL,
    created_date  DATETIME(6)  NULL,
    updated_by    VARCHAR(64)  NULL,
    updated_date  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_item_image_item FOREIGN KEY (item_id) REFERENCES item (id),
    UNIQUE KEY uk_item_image_file (item_id, media_file_id),
    KEY idx_item_image_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- rollback DROP TABLE item_image;
