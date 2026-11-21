-- liquibase formatted sql

-- changeset ERP-IAM:20261120270000-1
-- Recruitment (docs/analysis/06 §7.1), checked by recruitment-service; must match RecruitmentPermissions. Idempotent.
INSERT INTO permission (code, module, description)
VALUES ('RECRUITMENT_VIEW', 'RECRUITMENT', 'View recruitment catalogues, job postings, candidates and offers'),
       ('RECRUITMENT_MANAGE', 'RECRUITMENT', 'Manage recruitment catalogues, job postings, candidates and offers')
ON DUPLICATE KEY UPDATE module      = VALUES(module),
                        description = VALUES(description);

-- changeset ERP-IAM:20261120270000-2
-- Existing COMPANY_ADMIN and HR roles get what new companies get from EDefaultRole.
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
         JOIN permission p ON p.code IN ('RECRUITMENT_VIEW', 'RECRUITMENT_MANAGE')
WHERE r.code IN ('COMPANY_ADMIN', 'HR')
  AND r.deleted = 0;
