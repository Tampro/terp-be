-- liquibase formatted sql

-- changeset ERP-ASSET:20261120510000-1
-- Assignments of an asset (docs/analysis/06 8.2.2 Assign / Return): one open (ASSIGNED) row per asset at most,
-- checked by the service through the asset status. The employee lives in employee-service; name / no. / email are
-- copied for the history.
CREATE TABLE asset_assignment
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id           BIGINT        NOT NULL,
    asset_id             BIGINT        NOT NULL,
    employee_id          BIGINT        NOT NULL,
    employee_name        VARCHAR(200)  NULL,
    employee_no          VARCHAR(50)   NULL,
    employee_email       VARCHAR(255)  NULL,
    checkout_date        DATE          NOT NULL,
    expected_return_date DATE          NULL,
    checkout_condition   VARCHAR(10)   NOT NULL,
    checkout_notes       VARCHAR(1000) NULL,
    return_date          DATE          NULL,
    return_condition     VARCHAR(10)   NULL,
    return_notes         VARCHAR(1000) NULL,
    status               VARCHAR(10)   NOT NULL DEFAULT 'ASSIGNED',
    created_by           VARCHAR(64)   NULL,
    created_date         DATETIME(6)   NULL,
    updated_by           VARCHAR(64)   NULL,
    updated_date         DATETIME(6)   NULL,
    INDEX idx_asset_assignment_asset (asset_id),
    INDEX idx_asset_assignment_company (company_id, status),
    CONSTRAINT fk_asset_assignment_asset FOREIGN KEY (asset_id) REFERENCES asset (id)
);

-- changeset ERP-ASSET:20261120510000-2
-- Maintenance of an asset (docs/analysis/06 8.2.2 Maintenance modal and Maintenance History tab).
CREATE TABLE asset_maintenance
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id       BIGINT         NOT NULL,
    asset_id         BIGINT         NOT NULL,
    maintenance_type VARCHAR(12)    NOT NULL,
    start_date       DATE           NOT NULL,
    end_date         DATE           NULL,
    cost             DECIMAL(15, 2) NOT NULL DEFAULT 0,
    details          VARCHAR(2000)  NULL,
    supplier         VARCHAR(200)   NULL,
    status           VARCHAR(12)    NOT NULL,
    created_by       VARCHAR(64)    NULL,
    created_date     DATETIME(6)    NULL,
    updated_by       VARCHAR(64)    NULL,
    updated_date     DATETIME(6)    NULL,
    INDEX idx_asset_maintenance_asset (asset_id),
    INDEX idx_asset_maintenance_company (company_id, status, start_date),
    CONSTRAINT fk_asset_maintenance_asset FOREIGN KEY (asset_id) REFERENCES asset (id)
);
