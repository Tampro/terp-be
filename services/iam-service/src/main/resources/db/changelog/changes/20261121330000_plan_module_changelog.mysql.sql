-- liquibase formatted sql

-- changeset ERP-IAM:20261121330000-1
-- Modules a plan includes (ERP plan feature list). No row for a plan = every module, so existing plans keep working.
CREATE TABLE plan_module
(
    plan_id BIGINT      NOT NULL,
    module  VARCHAR(40) NOT NULL,
    PRIMARY KEY (plan_id, module),
    CONSTRAINT fk_plan_module_plan FOREIGN KEY (plan_id) REFERENCES plan (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- changeset ERP-IAM:20261121330000-2
-- Coupon description of the ERP coupon form.
ALTER TABLE coupon
    ADD COLUMN description VARCHAR(500) NULL AFTER name;
