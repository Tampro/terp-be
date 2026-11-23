-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261123900000-1
-- Calendar sync (ERP manual: holidays): when on, notification-service adds the holiday to the company's Google
-- Calendar. Existing holidays stay off.
ALTER TABLE holiday ADD COLUMN calendar_sync BOOLEAN NOT NULL DEFAULT FALSE;
