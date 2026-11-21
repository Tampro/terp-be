-- liquibase formatted sql

-- changeset ERP-IAM:20261121310000-1
-- Mobile number on the user profile (ERP sign-in & profile: name, email, mobile, photo). Optional, digits and + ( ) - . space.
ALTER TABLE user_account
    ADD COLUMN mobile VARCHAR(30) NULL AFTER full_name;
