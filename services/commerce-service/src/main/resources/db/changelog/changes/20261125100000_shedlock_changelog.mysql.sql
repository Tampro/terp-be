-- liquibase formatted sql

-- changeset ERP-COMMERCE:20261125100000-1
-- Scheduled job locks (ShedLock): one row per job, so a job runs on one instance at a time.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
-- rollback DROP TABLE shedlock;
