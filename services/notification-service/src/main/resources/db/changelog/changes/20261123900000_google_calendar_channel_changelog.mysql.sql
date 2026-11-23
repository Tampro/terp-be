-- liquibase formatted sql

-- changeset ERP-NOTIFICATION:20261123900000-1
-- Channel GOOGLE_CALENDAR (15 characters) does not fit VARCHAR(10).
ALTER TABLE delivery MODIFY channel VARCHAR(20) NOT NULL;
