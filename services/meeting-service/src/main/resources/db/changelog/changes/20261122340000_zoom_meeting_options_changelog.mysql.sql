-- liquibase formatted sql

-- changeset ERP-MEETING:20261122340000-1
-- Zoom meeting options (ERP manual 11.4); existing meetings were created with Zoom's defaults off.
ALTER TABLE zoom_meeting
    ADD COLUMN host_video        BIT(1)      NOT NULL DEFAULT 0,
    ADD COLUMN participant_video BIT(1)      NOT NULL DEFAULT 0,
    ADD COLUMN waiting_room      BIT(1)      NOT NULL DEFAULT 0,
    ADD COLUMN auto_recording    VARCHAR(10) NOT NULL DEFAULT 'NONE';
-- rollback ALTER TABLE zoom_meeting DROP COLUMN host_video, DROP COLUMN participant_video, DROP COLUMN waiting_room, DROP COLUMN auto_recording;
