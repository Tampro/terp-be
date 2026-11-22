-- liquibase formatted sql

-- changeset ERP-COMMERCE:20261122700000-1
-- Cost per unit of a stocked sales invoice line, taken when the invoice is posted: the COGS of the invoice
-- (Dr 632 / Cr 156) and, at the same cost, of its returns (Dr 156 / Cr 632). NULL on drafts and older invoices.
ALTER TABLE sales_document_line
    ADD COLUMN unit_cost DECIMAL(19, 4) NULL;
