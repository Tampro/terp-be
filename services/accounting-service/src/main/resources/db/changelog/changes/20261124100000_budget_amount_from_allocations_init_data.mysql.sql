-- liquibase formatted sql

-- changeset ERP-ACCOUNTING:20261124100000-1
-- comment: Budget amount becomes the sum of its allocations (reference product + PO 2026-11-24); align existing rows.
UPDATE budget b
SET b.amount = (SELECT COALESCE(SUM(a.allocated_amount), 0) FROM budget_allocation a WHERE a.budget_id = b.id);
