-- liquibase formatted sql

-- changeset ERP-IAM:20261121160000-1
-- Login History (docs/analysis/06 10.1.1): one row per successful login through IAM. user_id / company_id are null
-- for a super admin (no ERP profile); ip = the hop the gateway appended to X-Forwarded-For; browser / os parsed from
-- the User-Agent (kept as received, at most 500 chars).
CREATE TABLE login_history
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NULL,
    company_id   BIGINT       NULL,
    username     VARCHAR(100) NOT NULL,
    user_type    VARCHAR(20)  NOT NULL,
    ip_address   VARCHAR(64)  NULL,
    user_agent   VARCHAR(500) NULL,
    browser      VARCHAR(50)  NULL,
    os           VARCHAR(50)  NULL,
    login_at     DATETIME(6)  NOT NULL,
    deleted      BIT(1)       NOT NULL DEFAULT 0,
    INDEX idx_login_history_company (company_id, login_at),
    INDEX idx_login_history_user (user_id)
);
