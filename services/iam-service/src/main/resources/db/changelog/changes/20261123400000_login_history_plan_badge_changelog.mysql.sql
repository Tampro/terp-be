-- liquibase formatted sql

-- changeset ERP-IAM:20261123400000-1
-- Login history details (user manual §3.2): device type from the User-Agent, Accept-Language, the FE's
-- X-Client-Timezone and the roles held at login. city / country / isp stay NULL until a GeoIP source is chosen.
ALTER TABLE login_history
    ADD COLUMN device_type   VARCHAR(10)  NULL,
    ADD COLUMN language      VARCHAR(35)  NULL,
    ADD COLUMN timezone      VARCHAR(64)  NULL,
    ADD COLUMN role_at_login VARCHAR(255) NULL,
    ADD COLUMN city          VARCHAR(100) NULL,
    ADD COLUMN country       VARCHAR(100) NULL,
    ADD COLUMN isp           VARCHAR(150) NULL;
CREATE INDEX idx_login_history_ip ON login_history (ip_address);

-- changeset ERP-IAM:20261123400000-2
-- Plan "Free" flag and badge text (user manual §4.1).
ALTER TABLE plan
    ADD COLUMN is_free BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN badge   VARCHAR(50) NULL;
