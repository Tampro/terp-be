-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120160100-1
-- The four categories of the calendar legend (docs/analysis/06 §6.3.4). Fixed ids so the seed stays idempotent.
INSERT INTO holiday_category (id, company_id, name, color, deleted, created_by, created_date)
VALUES (1, NULL, 'National', '#E53935', 0, 'system', UTC_TIMESTAMP(6)),
       (2, NULL, 'Religious', '#8E24AA', 0, 'system', UTC_TIMESTAMP(6)),
       (3, NULL, 'Company Specific', '#1E88E5', 0, 'system', UTC_TIMESTAMP(6)),
       (4, NULL, 'Regional', '#43A047', 0, 'system', UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE name = VALUES(name), color = VALUES(color);
