-- liquibase formatted sql

-- changeset ERP-ORDER:20261129170000-1
-- The customer's email at checkout (token claim), AES-GCM like the phone: order and delivery emails go to it.
ALTER TABLE customer_order
    ADD COLUMN email_enc VARCHAR(512) NULL;
