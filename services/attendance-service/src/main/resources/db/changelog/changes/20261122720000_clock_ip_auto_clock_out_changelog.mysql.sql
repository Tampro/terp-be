-- liquibase formatted sql

-- changeset ERP-ATTENDANCE:20261122720000-1
-- IP restriction of self clock-in/out (FR-ERP-ATT-004): off by default; allowed_ips = comma-separated IPs / CIDR ranges.
ALTER TABLE attendance_setting
    ADD COLUMN ip_restriction_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER weekly_off_days,
    ADD COLUMN allowed_ips TEXT NULL AFTER ip_restriction_enabled;
-- rollback ALTER TABLE attendance_setting DROP COLUMN allowed_ips, DROP COLUMN ip_restriction_enabled;

-- changeset ERP-ATTENDANCE:20261122720000-2
-- A forgotten clock-out closed by the job at the shift end (FR-ERP-ATT-003); the index serves the job's scan of open records.
ALTER TABLE attendance_record
    ADD COLUMN auto_clocked_out BOOLEAN NOT NULL DEFAULT FALSE AFTER clock_out,
    ADD INDEX idx_attendance_record_open (work_date, clock_out);
-- rollback ALTER TABLE attendance_record DROP INDEX idx_attendance_record_open, DROP COLUMN auto_clocked_out;
