-- liquibase formatted sql

-- changeset ERP-CRM:20261124100000-1
-- Display name of the contract's creator (its owner), captured from the token at create; existing rows stay null.
ALTER TABLE contract ADD COLUMN owner_name VARCHAR(150) NULL AFTER description;
-- rollback ALTER TABLE contract DROP COLUMN owner_name;
