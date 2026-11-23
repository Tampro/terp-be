-- liquibase formatted sql

-- changeset ERP-COMMERCE:20261123300000-1
-- Manage Own: a holder of *_OWN lists only the documents they created (created_by = Keycloak sub).
ALTER TABLE sales_document ADD INDEX idx_sales_document_owner (company_id, kind, created_by);
ALTER TABLE sales_return ADD INDEX idx_sales_return_owner (company_id, created_by);
ALTER TABLE purchase_invoice ADD INDEX idx_purchase_invoice_owner (company_id, created_by);
ALTER TABLE purchase_return ADD INDEX idx_purchase_return_owner (company_id, created_by);

-- changeset ERP-COMMERCE:20261123300000-2
-- Client / vendor portal: documents of one customer / vendor.
ALTER TABLE sales_return ADD INDEX idx_sales_return_customer (company_id, customer_id);
ALTER TABLE purchase_return ADD INDEX idx_purchase_return_vendor (company_id, vendor_id);
