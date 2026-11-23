-- liquibase formatted sql

-- changeset ERP-COMMERCE:20261123900000-1
-- Sync to Google Calendar (ERP manual: sales invoice form): when on, posting the invoice adds its due date to the
-- company's Google Calendar (notification-service). Existing documents stay off.
ALTER TABLE sales_document ADD COLUMN sync_to_google_calendar BOOLEAN NOT NULL DEFAULT FALSE;
