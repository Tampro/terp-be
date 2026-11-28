package com.terp.erp.security;

/**
 * A B2C customer signed in to realm erp-customer (ADR-001). Carries no company and no permission: the service resolves
 * the shop's company from the request (slug) and the customer's own rows by {@code sub}.
 *
 * @param sub   Keycloak subject in realm erp-customer
 * @param azp   client the token was issued to (erp-shop-web, erp-shop-app, erp-shop-e2e)
 * @param email email claim, may be null
 */
public record CustomerPrincipal(String sub, String azp, String email) {
}
