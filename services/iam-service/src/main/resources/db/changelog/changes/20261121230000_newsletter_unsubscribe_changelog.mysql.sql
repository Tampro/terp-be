-- liquibase formatted sql

-- changeset ERP-IAM:20261121230000-1
-- One-click unsubscribe link of each newsletter subscriber (random, only allows removing that address).
ALTER TABLE newsletter_subscriber
    ADD COLUMN unsubscribe_token VARCHAR(64) NULL;

-- changeset ERP-IAM:20261121230000-2
UPDATE newsletter_subscriber
SET unsubscribe_token = CONCAT(REPLACE(UUID(), '-', ''), REPLACE(UUID(), '-', ''))
WHERE unsubscribe_token IS NULL;

-- changeset ERP-IAM:20261121230000-3
ALTER TABLE newsletter_subscriber
    MODIFY unsubscribe_token VARCHAR(64) NOT NULL,
    ADD CONSTRAINT uk_newsletter_unsubscribe UNIQUE (unsubscribe_token);
