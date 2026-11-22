-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261122800100-1
-- Reviews: SCHEDULED -> PENDING (manual 16: Pending -> In Progress -> Completed); IN_PROGRESS / COMPLETED kept.
UPDATE employee_review SET status = 'PENDING' WHERE status = 'SCHEDULED';
