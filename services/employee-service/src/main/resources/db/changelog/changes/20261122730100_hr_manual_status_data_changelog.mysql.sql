-- liquibase formatted sql

-- changeset ERP-EMPLOYEE:20261122730100-1
-- Warnings: DRAFT -> PENDING; ISSUED / ACKNOWLEDGED / EXPIRED -> APPROVED. expiry_date, acknowledgment_date and
-- employee_response are kept, so an old EXPIRED row still reads expired (APPROVED + expiry date passed).
UPDATE employee_warning SET status = 'PENDING' WHERE status = 'DRAFT';
UPDATE employee_warning SET status = 'APPROVED' WHERE status IN ('ISSUED', 'ACKNOWLEDGED', 'EXPIRED');

-- changeset ERP-EMPLOYEE:20261122730100-2
-- Complaints: SUBMITTED -> PENDING; UNDER_INVESTIGATION -> IN_REVIEW, or ASSIGNED when a handler is set; RESOLVED kept;
-- DISMISSED -> RESOLVED with the resolution action marked "Dismissed" (resolution date = complaint date if none).
-- TODO(assumption): no DISMISSED status in the manual — confirm with BA.
UPDATE employee_complaint SET status = 'PENDING' WHERE status = 'SUBMITTED';
UPDATE employee_complaint SET status = 'ASSIGNED' WHERE status = 'UNDER_INVESTIGATION' AND assigned_to_id IS NOT NULL;
UPDATE employee_complaint SET status = 'IN_REVIEW' WHERE status = 'UNDER_INVESTIGATION';
UPDATE employee_complaint
SET resolution_action = CASE WHEN resolution_action IS NULL OR resolution_action = '' THEN 'Dismissed'
                             ELSE CONCAT('Dismissed: ', resolution_action) END,
    resolution_date   = COALESCE(resolution_date, complaint_date),
    status            = 'RESOLVED'
WHERE status = 'DISMISSED';

-- changeset ERP-EMPLOYEE:20261122730100-3
-- Resignations: APPROVED / COMPLETED -> ACCEPTED (completed_at and the exit interview kept). Terminations: PLANNED ->
-- PENDING; IN_PROGRESS / COMPLETED -> APPROVED. Employees are NOT changed here: an old APPROVED resignation or
-- IN_PROGRESS termination whose employee was never terminated stays so (completed_at null shows which).
UPDATE employee_resignation SET status = 'ACCEPTED' WHERE status IN ('APPROVED', 'COMPLETED');
UPDATE employee_termination SET status = 'PENDING' WHERE status = 'PLANNED';
UPDATE employee_termination SET status = 'APPROVED' WHERE status IN ('IN_PROGRESS', 'COMPLETED');

-- changeset ERP-EMPLOYEE:20261122730100-4
-- Promotions from before the branch / department fields: the new designation's department and branch.
UPDATE employee_promotion p
    JOIN designation g ON g.id = p.new_designation_id
    JOIN department d ON d.id = g.department_id
SET p.new_department_id = g.department_id,
    p.new_branch_id     = d.branch_id
WHERE p.new_department_id IS NULL;
