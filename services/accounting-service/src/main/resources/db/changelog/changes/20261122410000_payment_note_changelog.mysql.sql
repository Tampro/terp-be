-- liquibase formatted sql

-- changeset ERP-ACCOUNTING:20261122410000-1
-- Customer payments (CP-) and vendor payments (VP-), ERP manual 12.4: PENDING -> CLEARED | CANCELLED.
-- amount = cash through the bank account = allocated_total - notes_total.
CREATE TABLE payment
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT         NOT NULL,
    kind            VARCHAR(10)    NOT NULL,
    payment_number  VARCHAR(30)    NOT NULL,
    payment_date    DATE           NOT NULL,
    party_id        BIGINT         NOT NULL,
    party_name      VARCHAR(255)   NOT NULL,
    bank_account_id BIGINT         NOT NULL,
    reference       VARCHAR(100)   NULL,
    notes           TEXT           NULL,
    allocated_total DECIMAL(19, 4) NOT NULL DEFAULT 0,
    notes_total     DECIMAL(19, 4) NOT NULL DEFAULT 0,
    amount          DECIMAL(19, 4) NOT NULL DEFAULT 0,
    status          VARCHAR(10)    NOT NULL,
    cleared_by      VARCHAR(64)    NULL,
    cleared_at      DATETIME(6)    NULL,
    cancelled_at    DATETIME(6)    NULL,
    deleted         BIT(1)         NOT NULL DEFAULT 0,
    created_by      VARCHAR(64)    NULL,
    updated_by      VARCHAR(64)    NULL,
    created_date    DATETIME(6)    NULL,
    updated_date    DATETIME(6)    NULL,
    CONSTRAINT uk_payment_number UNIQUE (company_id, payment_number),
    INDEX idx_payment_list (company_id, kind, status, payment_date),
    INDEX idx_payment_party (company_id, kind, party_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ACCOUNTING:20261122410000-2
-- One invoice of commerce-service per row; invoice_number is a copy for display.
CREATE TABLE payment_allocation
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id     BIGINT         NOT NULL,
    invoice_id     BIGINT         NOT NULL,
    invoice_number VARCHAR(30)    NULL,
    amount         DECIMAL(19, 4) NOT NULL,
    CONSTRAINT uk_payment_allocation UNIQUE (payment_id, invoice_id),
    CONSTRAINT fk_payment_allocation_payment FOREIGN KEY (payment_id) REFERENCES payment (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ACCOUNTING:20261122410000-3
-- Credit notes (CN-, from a completed sales return) and debit notes (DN-, from a completed purchase return),
-- ERP manual 12.5: DRAFT -> APPROVED -> PARTIAL -> APPLIED. return_key = return_id while the note is not deleted,
-- NULL once deleted: one live note per return, and a deleted draft frees the return.
CREATE TABLE account_note
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT         NOT NULL,
    kind          VARCHAR(10)    NOT NULL,
    note_number   VARCHAR(30)    NOT NULL,
    return_id     BIGINT         NOT NULL,
    return_key    BIGINT         NULL,
    return_number VARCHAR(30)    NULL,
    party_id      BIGINT         NOT NULL,
    party_name    VARCHAR(255)   NOT NULL,
    note_date     DATE           NOT NULL,
    total         DECIMAL(19, 4) NOT NULL DEFAULT 0,
    tax_amount    DECIMAL(19, 4) NOT NULL DEFAULT 0,
    balance       DECIMAL(19, 4) NOT NULL DEFAULT 0,
    status        VARCHAR(10)    NOT NULL,
    approved_by   VARCHAR(64)    NULL,
    approved_at   DATETIME(6)    NULL,
    notes         TEXT           NULL,
    deleted       BIT(1)         NOT NULL DEFAULT 0,
    created_by    VARCHAR(64)    NULL,
    updated_by    VARCHAR(64)    NULL,
    created_date  DATETIME(6)    NULL,
    updated_date  DATETIME(6)    NULL,
    CONSTRAINT uk_account_note_number UNIQUE (company_id, note_number),
    CONSTRAINT uk_account_note_return UNIQUE (company_id, kind, return_key),
    INDEX idx_account_note_list (company_id, kind, status, note_date),
    INDEX idx_account_note_party (company_id, kind, party_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-ACCOUNTING:20261122410000-4
-- Credit / debit notes applied in a payment; they lower the note balance when the payment clears.
CREATE TABLE payment_note
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT         NOT NULL,
    note_id    BIGINT         NOT NULL,
    amount     DECIMAL(19, 4) NOT NULL,
    CONSTRAINT uk_payment_note UNIQUE (payment_id, note_id),
    CONSTRAINT fk_payment_note_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT fk_payment_note_note FOREIGN KEY (note_id) REFERENCES account_note (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
