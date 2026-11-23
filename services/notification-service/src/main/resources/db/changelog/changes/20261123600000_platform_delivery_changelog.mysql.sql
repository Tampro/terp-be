-- liquibase formatted sql

-- changeset ERP-NOTIFICATION:20261123600000-1
-- Platform events (routing key platform.*, companyId null in the envelope) get deliveries with no company: they go
-- to the super admin's webhooks and platform team addresses and are listed to super admins only.
ALTER TABLE delivery MODIFY company_id BIGINT NULL;
