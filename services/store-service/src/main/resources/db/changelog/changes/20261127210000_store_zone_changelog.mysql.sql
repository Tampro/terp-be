-- liquibase formatted sql

-- changeset ERP-STORE:20261127210000-1
-- Delivery zones of a store: one GeoJSON Polygon ring each, WGS84 (SRID 4326). company_id is copied from the store so
-- the point -> store lookup filters the company in SQL. Hard delete: a zone keeps no history.
CREATE TABLE store_zone
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    store_id     BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    area         POLYGON      NOT NULL SRID 4326,
    active       BIT(1)       NOT NULL DEFAULT 1,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    SPATIAL INDEX sx_store_zone_area (area),
    INDEX idx_store_zone_store (store_id),
    CONSTRAINT fk_store_zone_store FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE CASCADE
);
