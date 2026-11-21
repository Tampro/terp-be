-- liquibase formatted sql

-- changeset ERP-IAM:20261118140000-1
-- Plans agreed with the PO on 2026-11-18 (prices in VND). Inserted only when the code does not exist yet, so a
-- price later changed through the super-admin API is never overwritten.
INSERT INTO plan (code, name, description, monthly_price, yearly_price, max_users, max_employees, storage_limit_gb,
                  trial_days, is_default, enabled, deleted, created_by, created_date)
SELECT 'STARTER', 'Starter', 'Gói mặc định cho công ty mới, dùng thử 14 ngày', 490000, 4900000, 10, 50, 5,
       14, 1, 1, 0, 'system', UTC_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM plan WHERE code = 'STARTER' AND deleted = 0);

INSERT INTO plan (code, name, description, monthly_price, yearly_price, max_users, max_employees, storage_limit_gb,
                  trial_days, is_default, enabled, deleted, created_by, created_date)
SELECT 'PRO', 'Pro', 'Gói cho doanh nghiệp vừa', 990000, 9900000, 50, 500, 50,
       0, 0, 1, 0, 'system', UTC_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM plan WHERE code = 'PRO' AND deleted = 0);

-- changeset ERP-IAM:20261118140000-2
-- Exactly one default plan: Starter.
UPDATE plan
SET is_default = CASE WHEN code = 'STARTER' THEN 1 ELSE 0 END
WHERE deleted = 0;
