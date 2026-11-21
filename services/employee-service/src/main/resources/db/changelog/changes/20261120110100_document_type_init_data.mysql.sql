-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261120110100-1
-- The three default document slots of Add Employee (docs/analysis/06 §6.1). Fixed ids so the seed stays idempotent.
INSERT INTO document_type (id, company_id, name, is_default, deleted, created_by, created_date)
VALUES (1, NULL, 'Identity Proof', 1, 0, 'system', UTC_TIMESTAMP(6)),
       (2, NULL, 'Address Proof', 1, 0, 'system', UTC_TIMESTAMP(6)),
       (3, NULL, 'Educational Certificates', 1, 0, 'system', UTC_TIMESTAMP(6)),
       (4, NULL, 'Experience Letters', 0, 0, 'system', UTC_TIMESTAMP(6)),
       (5, NULL, 'Medical Certificates', 0, 0, 'system', UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE name = VALUES(name), is_default = VALUES(is_default);
