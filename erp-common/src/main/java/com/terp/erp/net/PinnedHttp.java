package com.terp.erp.net;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;

/**
 * HTTP client for URLs {@link UrlGuard} accepted, closing its DNS-rebinding window: a connection is opened only to
 * addresses this client's own DNS resolver returned and checked with {@link UrlGuard#isPublic} (every address must
 * be public, as in {@link UrlGuard#check}). The name is checked and used in the same lookup, while TLS still
 * verifies the certificate and SNI against the host name. Redirects, retries, cookies and system proxies are off.
 * Timeouts: connect, then {@code response} for the answer to start and between reads (no overall deadline).
 */
public class PinnedHttp {
    /** status null = no HTTP answer, failure TIMEOUT or CONNECTION_ERROR then; body only when it was asked for. */
    public record Exchange(Integer status, byte[] body, boolean truncated, String failure) {
        @Override
        public String toString() {
            return "Exchange[status=" + status + ", failure=" + failure + "]";
        }
    }

    private final CloseableHttpClient client;

    public PinnedHttp(Duration connect, Duration response, String userAgent) {
        this(connect, response, userAgent, InetAddress::getAllByName);
    }

    public PinnedHttp(Duration connect, Duration response, String userAgent, UrlGuard.HostResolver lookup) {
        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(new PublicOnlyResolver(lookup))
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.of(connect))
                                .setSocketTimeout(Timeout.of(response)).build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.of(response)).build())
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .setUserAgent(userAgent)
                .build();
    }

    /** maxBody 0 = discard the body; otherwise read at most maxBody bytes (truncated = there was more). */
    public Exchange execute(ClassicHttpRequest request, int maxBody) {
        try {
            return client.execute(request, r -> {
                HttpEntity e = r.getEntity();
                byte[] body = null;
                boolean truncated = false;
                if (e != null && maxBody > 0) {
                    try (InputStream in = e.getContent()) {
                        byte[] b = in.readNBytes(maxBody + 1);
                        truncated = b.length > maxBody;
                        body = truncated ? Arrays.copyOf(b, maxBody) : b;
                    }
                } else {
                    EntityUtils.consume(e);
                }
                return new Exchange(r.getCode(), body, truncated, null);
            });
        } catch (InterruptedIOException e) {
            // connect and socket timeouts are InterruptedIOExceptions in HttpClient 5
            return new Exchange(null, null, false, "TIMEOUT");
        } catch (IOException e) {
            return new Exchange(null, null, false, "CONNECTION_ERROR");
        }
    }

    /** Resolves like the system resolver, then refuses the host unless every address is public. */
    static final class PublicOnlyResolver implements DnsResolver {
        private final UrlGuard.HostResolver lookup;

        PublicOnlyResolver(UrlGuard.HostResolver lookup) {
            this.lookup = lookup;
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] all = lookup.resolve(host);
            if (all == null || all.length == 0) {
                throw new UnknownHostException("no address");
            }
            for (InetAddress a : all) {
                if (!UrlGuard.isPublic(a)) {
                    throw new UnknownHostException("the address is not public");
                }
            }
            return all;
        }

        @Override
        public String resolveCanonicalHostname(String host) {
            return host;
        }
    }
}
