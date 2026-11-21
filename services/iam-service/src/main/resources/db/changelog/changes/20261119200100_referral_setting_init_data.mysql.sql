-- liquibase formatted sql

-- changeset ERP-IAM:20261119200100-1
-- TODO(assumption): 10 % commission (WorkDo default) and a 500.000 VND payout threshold (WorkDo uses 50 in its
-- currency; amounts here are in the default currency, VND). Inserted once; the super admin edits it afterwards.
INSERT INTO referral_setting (id, enabled, commission_percentage, threshold_amount, guidelines, updated_by, updated_date)
VALUES (1, 1, 10.00, 500000.00, NULL, 'system', UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE id = id;
