-- liquibase formatted sql

-- changeset ERP-PAYROLL:20261120470000-1
-- Employee salary structures (docs/analysis/06 8.1.3): basic salary + components. The employee lives in
-- employee-service; name / no. / email are copied here (refreshed on every save) so the list can be searched without
-- a remote call per row. One ACTIVE structure per employee (checked by the service).
CREATE TABLE employee_salary
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id     BIGINT         NOT NULL,
    employee_id    BIGINT         NOT NULL,
    employee_name  VARCHAR(200)   NULL,
    employee_no    VARCHAR(50)    NULL,
    employee_email VARCHAR(255)   NULL,
    basic_salary   DECIMAL(15, 2) NOT NULL,
    status         VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    notes          VARCHAR(1000)  NULL,
    deleted        BIT(1)         NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)    NULL,
    created_date   DATETIME(6)    NULL,
    updated_by     VARCHAR(64)    NULL,
    updated_date   DATETIME(6)    NULL,
    INDEX idx_employee_salary_company (company_id),
    INDEX idx_employee_salary_employee (employee_id)
);

-- changeset ERP-PAYROLL:20261120470000-2
-- Components of a structure; value = an override of the component's own value (same unit), null = the component's.
CREATE TABLE employee_salary_component
(
    salary_id    BIGINT         NOT NULL,
    component_id BIGINT         NOT NULL,
    value        DECIMAL(15, 2) NULL,
    PRIMARY KEY (salary_id, component_id),
    CONSTRAINT fk_employee_salary_component_salary FOREIGN KEY (salary_id) REFERENCES employee_salary (id),
    CONSTRAINT fk_employee_salary_component_component FOREIGN KEY (component_id) REFERENCES salary_component (id)
);
