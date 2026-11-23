-- liquibase formatted sql

-- changeset ERP-MEDIA:20261123090000-1
-- Where each file's bytes live (LOCAL volume or S3/WASABI bucket). Existing rows were all written to the volume.
ALTER TABLE media_file
    ADD COLUMN storage_driver VARCHAR(10) NOT NULL DEFAULT 'LOCAL' AFTER storage_key;
