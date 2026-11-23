-- liquibase formatted sql

-- changeset ERP-RECRUITMENT:20261123200000-1
-- User manual §17 "Offers: salary, bonus, equity". equity is free text (TODO(assumption) in Offer.java).
ALTER TABLE offer
    ADD COLUMN bonus  DECIMAL(19, 4) NULL AFTER salary,
    ADD COLUMN equity VARCHAR(255)   NULL AFTER bonus;
