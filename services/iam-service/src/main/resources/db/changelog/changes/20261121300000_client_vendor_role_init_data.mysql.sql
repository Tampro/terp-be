-- liquibase formatted sql

-- changeset ERP-IAM:20261121300000-1
-- New companies get CLIENT and VENDOR from EDefaultRole; this adds them to companies created before.
-- role has no unique key on (company_id, code), so insert-if-missing is NOT EXISTS, not ON DUPLICATE KEY.
INSERT INTO role (company_id, code, name, description, system_role, deleted, created_by, created_date)
SELECT c.id, r.code, r.name, NULL, 1, 0, 'system', UTC_TIMESTAMP(6)
FROM company c
         JOIN (SELECT 'CLIENT' AS code, 'Client' AS name
               UNION ALL SELECT 'VENDOR', 'Vendor') r
WHERE NOT EXISTS (SELECT 1
                  FROM role x
                  WHERE x.company_id = c.id
                    AND x.code = r.code
                    AND x.deleted = 0);
