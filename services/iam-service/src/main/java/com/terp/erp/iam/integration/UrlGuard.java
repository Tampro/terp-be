package com.terp.erp.iam.integration;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.settings.ESettingsError;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * SSRF guard for URLs a company types in (Slack webhook, webhook endpoints) and the server then calls. Rules:
 * https only; no user-info; port 443 or 8443 (or none); a host name with a dot that is not localhost / *.local /
 * *.internal / *.localhost (so docker service names such as "keycloak" are refused); and every address the name
 * resolves to (or the IP literal) must be public: not 0.0.0.0/8, loopback, link-local (169.254/16 incl. cloud
 * metadata, fe80::/10), private (10/8, 172.16/12, 192.168/16), CGNAT 100.64/10, unique-local fc00::/7, multicast,
 * broadcast; IPv4-mapped IPv6 is checked as its IPv4 address. Redirects are never followed (see {@link OutboundHttp}).
 * Residual risk: the name is resolved here and again by the HTTP client (DNS rebinding window); pinning the address
 * would need a custom TLS socket factory. TODO: pin when dispatch is built.
 */
@Component
public class UrlGuard {
    /** Resolves a host to its addresses; tests inject a fake so they never hit DNS. */
    @FunctionalInterface
    public interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private static final Set<Integer> PORTS = Set.of(-1, 443, 8443);
    private final HostResolver resolver;

    public UrlGuard() {
        this(InetAddress::getAllByName);
    }

    public UrlGuard(HostResolver resolver) {
        this.resolver = resolver;
    }

    /** Shape only (no DNS): used when a URL is saved. allowedHost null = any public host. */
    public URI checkSyntax(String url, String allowedHost) {
        URI u;
        try {
            u = new URI(url == null ? "" : url.trim());
        } catch (URISyntaxException e) {
            throw refused("malformed URL");
        }
        if (!"https".equalsIgnoreCase(u.getScheme())) {
            throw refused("only https is allowed");
        }
        if (u.getRawUserInfo() != null) {
            throw refused("credentials in the URL");
        }
        if (!PORTS.contains(u.getPort())) {
            throw refused("only port 443 or 8443");
        }
        String host = u.getHost();
        if (host == null || host.isBlank()) {
            throw refused("no host");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) {
            // IPv6 literal: decided by the address check.
            checkAddress(literal(host.substring(1, host.length() - 1)));
        } else {
            if (host.endsWith(".")) {
                host = host.substring(0, host.length() - 1);
            }
            if (!host.contains(".") || host.equals("localhost") || host.endsWith(".localhost")
                    || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".lan")) {
                throw refused("internal host name");
            }
            if (host.matches("[0-9.]+")) {
                checkAddress(literal(host));
            }
        }
        if (allowedHost != null && !host.equals(allowedHost)) {
            throw refused("the host must be " + allowedHost);
        }
        return u;
    }

    /** Shape + DNS: used right before the call. */
    public URI check(String url, String allowedHost) {
        URI u = checkSyntax(url, allowedHost);
        String host = u.getHost();
        if (host.startsWith("[")) {
            return u;
        }
        InetAddress[] all;
        try {
            all = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw refused("the host does not resolve");
        }
        if (all == null || all.length == 0) {
            throw refused("the host does not resolve");
        }
        for (InetAddress a : all) {
            checkAddress(a);
        }
        return u;
    }

    private InetAddress literal(String ip) {
        try {
            // A literal never triggers a DNS lookup.
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            throw refused("malformed address");
        }
    }

    static boolean isPublic(InetAddress a) {
        if (a instanceof Inet6Address v6) {
            byte[] b = v6.getAddress();
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= b[i] == 0;
            }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isPublic(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
            if ((b[0] & 0xfe) == 0xfc) {
                return false; // unique local fc00::/7
            }
        }
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return false;
        }
        if (a instanceof Inet4Address) {
            byte[] b = a.getAddress();
            int b0 = b[0] & 0xff;
            int b1 = b[1] & 0xff;
            if (b0 == 0 || b0 == 127 || (b0 == 100 && b1 >= 64 && b1 <= 127) || (b0 == 169 && b1 == 254)
                    || b0 >= 240) {
                return false; // this network, loopback, CGNAT, link-local, reserved + broadcast
            }
        }
        return true;
    }

    private void checkAddress(InetAddress a) {
        if (!isPublic(a)) {
            throw refused("the address is not public");
        }
    }

    private static ApiException refused(String reason) {
        return ApiException.badRequest(ESettingsError.SETTINGS_013, reason);
    }
}
