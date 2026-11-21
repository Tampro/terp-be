-- liquibase formatted sql

-- changeset ERP-IAM:20261119200000-1
-- Referral program (docs/analysis/06 §3.2.6): every company has a code; a company may be referred by another.
ALTER TABLE company
    ADD COLUMN referral_code          VARCHAR(20) NULL,
    ADD COLUMN referred_by_company_id BIGINT      NULL,
    ADD CONSTRAINT fk_company_referred_by FOREIGN KEY (referred_by_company_id) REFERENCES company (id);

-- changeset ERP-IAM:20261119200000-2
-- Existing companies get REF + 6-digit id (same rule as new ones), then the code becomes unique.
UPDATE company SET referral_code = CONCAT('REF', LPAD(id, 6, '0')) WHERE referral_code IS NULL;
ALTER TABLE company ADD CONSTRAINT uk_company_referral_code UNIQUE (referral_code);

-- changeset ERP-IAM:20261119200000-3
-- One settings row (id = 1), edited by the super admin.
CREATE TABLE referral_setting
(
    id                    BIGINT         NOT NULL PRIMARY KEY,
    enabled               BIT(1)         NOT NULL,
    commission_percentage DECIMAL(5, 2)  NOT NULL,
    threshold_amount      DECIMAL(15, 2) NOT NULL,
    guidelines            TEXT           NULL,
    updated_by            VARCHAR(64)    NULL,
    updated_date          DATETIME(6)    NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261119200000-4
-- One row per approved bank-transfer order of a referred company; percentage frozen at approval.
CREATE TABLE referral_commission
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    referrer_company_id   BIGINT         NOT NULL,
    referred_company_id   BIGINT         NOT NULL,
    order_id              BIGINT         NOT NULL,
    order_amount          DECIMAL(15, 2) NOT NULL,
    commission_percentage DECIMAL(5, 2)  NOT NULL,
    amount                DECIMAL(15, 2) NOT NULL,
    created_by            VARCHAR(64)    NULL,
    updated_by            VARCHAR(64)    NULL,
    created_date          DATETIME(6)    NULL,
    updated_date          DATETIME(6)    NULL,
    CONSTRAINT uk_referral_commission_order UNIQUE (order_id),
    INDEX idx_referral_commission_referrer (referrer_company_id),
    INDEX idx_referral_commission_referred (referred_company_id),
    CONSTRAINT fk_rc_referrer FOREIGN KEY (referrer_company_id) REFERENCES company (id),
    CONSTRAINT fk_rc_referred FOREIGN KEY (referred_company_id) REFERENCES company (id),
    CONSTRAINT fk_rc_order FOREIGN KEY (order_id) REFERENCES plan_order (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261119200000-5
CREATE TABLE payout_request
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id   BIGINT         NOT NULL,
    amount       DECIMAL(15, 2) NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    notes        VARCHAR(500)   NULL,
    processed_at DATETIME(6)    NULL,
    processed_by VARCHAR(64)    NULL,
    created_by   VARCHAR(64)    NULL,
    updated_by   VARCHAR(64)    NULL,
    created_date DATETIME(6)    NULL,
    updated_date DATETIME(6)    NULL,
    INDEX idx_payout_company (company_id, status),
    INDEX idx_payout_status (status),
    CONSTRAINT fk_payout_company FOREIGN KEY (company_id) REFERENCES company (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
