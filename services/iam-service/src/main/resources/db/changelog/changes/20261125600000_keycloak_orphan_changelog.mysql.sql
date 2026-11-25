-- liquibase formatted sql

-- changeset ERP-IAM:20261125600000-1
-- Keycloak users left behind when a user creation rolled back and the compensating delete failed too (Keycloak
-- down). KeycloakOrphanJob retries the delete; no user_account row ever points at these ids.
CREATE TABLE keycloak_orphan (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    keycloak_id VARCHAR(64)  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_try_at TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_keycloak_orphan UNIQUE (keycloak_id)
);
-- rollback DROP TABLE keycloak_orphan;
