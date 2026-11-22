-- liquibase formatted sql

-- changeset ERP-ATTENDANCE:20261122330000-1
-- How the time was recorded (ERP manual 11.5); existing rows were typed by hand.
ALTER TABLE time_entry ADD COLUMN entry_type VARCHAR(15) NOT NULL DEFAULT 'MANUAL' AFTER hours;
-- rollback ALTER TABLE time_entry DROP COLUMN entry_type;
