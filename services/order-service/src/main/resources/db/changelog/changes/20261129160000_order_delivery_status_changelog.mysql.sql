-- liquibase formatted sql

-- changeset ERP-ORDER:20261129160000-1
-- Latest delivery status of a paid order (delivery.status.changed from delivery-service), shown to the customer.
ALTER TABLE customer_order
    ADD COLUMN delivery_status    VARCHAR(16) NULL,
    ADD COLUMN delivery_status_at DATETIME(6) NULL;
