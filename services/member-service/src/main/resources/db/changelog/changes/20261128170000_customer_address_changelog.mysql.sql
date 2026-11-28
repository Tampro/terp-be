-- liquibase formatted sql

-- changeset ERP-MEMBER:20261128170000-1
-- Delivery addresses of a membership (at most 10, one default). Personal data: the phone is stored AES-256-GCM
-- encrypted (phone_enc, FieldCipher); name and address in clear, deleted when the member leaves or the company is
-- anonymised (MemberAnonymizer). Hard delete.
CREATE TABLE customer_address
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT        NOT NULL,
    customer_id    BIGINT        NOT NULL,
    label          VARCHAR(30)   NOT NULL,
    recipient_name VARCHAR(50)   NOT NULL,
    phone_enc      VARCHAR(255)  NOT NULL,
    postcode       VARCHAR(5)    NOT NULL,
    address1       VARCHAR(200)  NOT NULL,
    address2       VARCHAR(200)  NULL,
    lat            DECIMAL(9, 6) NOT NULL,
    lon            DECIMAL(9, 6) NOT NULL,
    delivery_note  VARCHAR(200)  NULL,
    is_default     BIT(1)        NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)   NULL,
    created_date   DATETIME(6)   NULL,
    updated_by     VARCHAR(64)   NULL,
    updated_date   DATETIME(6)   NULL,
    INDEX idx_address_customer (customer_id, is_default),
    INDEX idx_address_company (company_id),
    CONSTRAINT fk_address_customer FOREIGN KEY (customer_id) REFERENCES customer (id) ON DELETE CASCADE
);
