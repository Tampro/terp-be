-- liquibase formatted sql

-- changeset ERP-IAM:20261119180100-1
-- VND is the default: plan prices are in VND (Starter 490.000, Pro 990.000). USD and KRW for TERP's markets.
-- TODO(assumption): currency list — confirm with the PO. Idempotent; a row edited later is not overwritten.
INSERT INTO currency (code, name, symbol, description, is_default, created_by, created_date)
VALUES ('VND', 'Vietnamese Dong', '₫', 'Default platform currency', 1, 'system', UTC_TIMESTAMP(6)),
       ('USD', 'US Dollar', '$', NULL, 0, 'system', UTC_TIMESTAMP(6)),
       ('KRW', 'South Korean Won', '₩', NULL, 0, 'system', UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE code = code;
