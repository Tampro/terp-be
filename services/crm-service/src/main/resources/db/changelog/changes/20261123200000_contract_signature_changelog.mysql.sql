-- liquibase formatted sql

-- changeset ERP-CRM:20261123200000-1
-- Hand-drawn acceptance of a contract, one per side (TERP Core contract_signatures, keyed here by side instead of
-- user). Not a qualified digital signature. The PNG (<= 200 KB) stays in crm: the client signer has no media-library
-- permission. content_sha256 = hash of the contract's signed fields when the side signed (ContractContentHash v1).
CREATE TABLE contract_signature
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT       NOT NULL,
    contract_id    BIGINT       NOT NULL,
    signer_type    VARCHAR(10)  NOT NULL,
    signer_user_id BIGINT       NULL,
    signer_name    VARCHAR(150) NOT NULL,
    signer_email   VARCHAR(255) NULL,
    signature_png  MEDIUMBLOB   NOT NULL,
    content_sha256 CHAR(64)     NOT NULL,
    ip             VARCHAR(64)  NULL,
    user_agent     VARCHAR(255) NULL,
    signed_at      DATETIME(6)  NOT NULL,
    created_by     VARCHAR(64)  NULL,
    updated_by     VARCHAR(64)  NULL,
    created_date   DATETIME(6)  NULL,
    updated_date   DATETIME(6)  NULL,
    CONSTRAINT uk_contract_signature_side UNIQUE (contract_id, signer_type),
    CONSTRAINT fk_contract_signature_contract FOREIGN KEY (contract_id) REFERENCES contract (id),
    INDEX idx_contract_signature_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
