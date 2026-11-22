package com.terp.erp.iam.integration;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.settings.ESettingsError;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * IAM's SSRF guard: the shared rules of {@link com.terp.erp.net.UrlGuard} (erp-common, also used by
 * notification-service for real deliveries), with a refusal answered as SETTINGS_013.
 */
@Component
public class UrlGuard {
    /** Resolves a host to its addresses; tests inject a fake so they never hit DNS. */
    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final com.terp.erp.net.UrlGuard rules;

    public UrlGuard() {
        this(InetAddress::getAllByName);
    }

    public UrlGuard(HostResolver resolver) {
        this.rules = new com.terp.erp.net.UrlGuard(resolver::resolve);
    }

    /** Shape only (no DNS): used when a URL is saved. allowedHost null = any public host. */
    public URI checkSyntax(String url, String allowedHost) {
        try {
            return rules.checkSyntax(url, allowedHost);
        } catch (com.terp.erp.net.UrlGuard.RefusedException e) {
            throw refused(e);
        }
    }

    /** Shape + DNS: used right before the call. */
    public URI check(String url, String allowedHost) {
        try {
            return rules.check(url, allowedHost);
        } catch (com.terp.erp.net.UrlGuard.RefusedException e) {
            throw refused(e);
        }
    }

    static boolean isPublic(InetAddress a) {
        return com.terp.erp.net.UrlGuard.isPublic(a);
    }

    private static ApiException refused(com.terp.erp.net.UrlGuard.RefusedException e) {
        return ApiException.badRequest(ESettingsError.SETTINGS_013, e.reason());
    }
}
