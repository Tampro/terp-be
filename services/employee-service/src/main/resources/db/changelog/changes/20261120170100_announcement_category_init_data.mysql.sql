-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120170100-1
-- System announcement categories. Fixed ids so the seed stays idempotent.
-- TODO(assumption): the spec names no categories; these four are a proposal — confirm with BA.
INSERT INTO announcement_category (id, company_id, name, color, deleted, created_by, created_date)
VALUES (1, NULL, 'General', '#546E7A', 0, 'system', UTC_TIMESTAMP(6)),
       (2, NULL, 'Policy', '#3949AB', 0, 'system', UTC_TIMESTAMP(6)),
       (3, NULL, 'Event', '#00897B', 0, 'system', UTC_TIMESTAMP(6)),
       (4, NULL, 'HR Update', '#F4511E', 0, 'system', UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE name = VALUES(name), color = VALUES(color);
