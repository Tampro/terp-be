-- liquibase formatted sql

-- changeset ERP-STORE:20261128090000-1
-- Day-offs of a store: whole local dates (store.time_zone) without orders. One row per (store, date); hard delete.
CREATE TABLE store_day_off
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT       NOT NULL,
    store_id     BIGINT       NOT NULL,
    off_date     DATE         NOT NULL,
    reason       VARCHAR(255) NULL,
    created_by   VARCHAR(64)  NULL,
    created_date DATETIME(6)  NULL,
    updated_by   VARCHAR(64)  NULL,
    updated_date DATETIME(6)  NULL,
    UNIQUE KEY uk_store_day_off (store_id, off_date),
    CONSTRAINT fk_store_day_off_store FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE CASCADE
);
