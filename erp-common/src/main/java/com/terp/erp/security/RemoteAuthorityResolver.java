package com.terp.erp.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.Nulls;

import java.time.Duration;

/**
 * For services other than IAM: asks {@code GET /api/v1/me/authorities} on the IAM service with the caller's own
 * token, so no service credential exists and IAM applies the same rules as for the FE. Results are cached per token
 * for {@code ttl} — that is the delay before a role change reaches this service.
 *
 * <p>Stale if error (PO 2026-11-25): when IAM cannot answer — connection refused, timeout, 5xx — the last
 * authorities IAM returned for this same token are used for up to {@code grace} (default 5 min) after they were
 * fetched, so an IAM restart or deploy does not stop every other service. A 4xx from IAM (account locked, company
 * suspended, token refused) is final: it is never answered from the stale copy. A token IAM never answered for
 * still fails closed (500), and an expired token is refused before this class runs (JWT validation). The cost: while
 * IAM is down, a role change or lock reaches this service up to {@code grace} late.
 *
 * <p>Not retried — a retry storm on IAM would make an outage worse; after a failure IAM is not called again for
 * {@value #BACKOFF_MS} ms (requests in that window go straight to the stale copy or fail).
 *
 * <p>Login as Company: the {@value #ACT_AS_HEADER} header of the request is passed on, so IAM resolves a super admin
 * as the company's administrator for that session; the cache is per token and header.
 */
public class RemoteAuthorityResolver implements AuthorityResolver {
    /** Session key of a super admin's "Login as Company" (docs/analysis/06 §3.2.1), checked by IAM. */
    public static final String ACT_AS_HEADER = "X-Act-As-Company";

    private static final ParameterizedTypeReference<ApiMessageDto<ErpPrincipal>> TYPE = new ParameterizedTypeReference<>() {
    };

    static final long BACKOFF_MS = 3_000;
    private static final long WARN_EVERY_MS = 60_000;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RemoteAuthorityResolver.class);

    private final RestClient client;
    private final Cache<String, ErpPrincipal> cache;
    /** Last good answer per key, kept for ttl + grace; read only when IAM cannot answer. */
    private final Cache<String, ErpPrincipal> lastGood;
    private final java.util.concurrent.atomic.AtomicLong iamDownUntil = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong lastWarn = new java.util.concurrent.atomic.AtomicLong();

    public RemoteAuthorityResolver(String iamBaseUrl, Duration ttl) {
        this(iamBaseUrl, ttl, Duration.ofMinutes(5));
    }

    public RemoteAuthorityResolver(String iamBaseUrl, Duration ttl, Duration grace) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(3)));
        this.client = RestClient.builder().baseUrl(Nulls.nonNull(iamBaseUrl)).requestFactory(rf).build();
        this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(10_000).build();
        this.lastGood = Caffeine.newBuilder().expireAfterWrite(ttl.plus(grace)).maximumSize(10_000).build();
    }

    @Override
    public ErpPrincipal resolve(Jwt jwt) {
        String actAs = actAsHeader();
        String token = jwt.getTokenValue();
        String key = actAs == null ? token : token + "|" + actAs;
        ErpPrincipal cached = cache.getIfPresent(key);
        return cached != null ? cached : fetchOrStale(key, token, actAs);
    }

    private ErpPrincipal fetchOrStale(String key, String token, String actAs) {
        if (System.currentTimeMillis() >= iamDownUntil.get()) {
            try {
                ErpPrincipal p = fetch(token, actAs);
                cache.put(key, p);
                lastGood.put(key, p);
                return p;
            } catch (IamUnavailable e) {
                iamDownUntil.set(System.currentTimeMillis() + BACKOFF_MS);
            }
        }
        ErpPrincipal stale = lastGood.getIfPresent(key);
        long now = System.currentTimeMillis();
        long prev = lastWarn.get();
        if (now - prev >= WARN_EVERY_MS && lastWarn.compareAndSet(prev, now)) {
            log.warn("IAM authority lookup unavailable: {}", stale != null
                    ? "answering from the last good authorities (stale if error)" : "no earlier answer, refused");
        }
        if (stale == null) {
            throw new AuthenticationServiceException("IAM authority lookup failed");
        }
        return stale; // not put in the fresh cache: the next request after the backoff tries IAM again
    }

    /** IAM did not answer (connection, timeout, 5xx) — the only case the stale copy may cover. */
    private static final class IamUnavailable extends RuntimeException {
        IamUnavailable(Throwable cause) {
            super(cause);
        }
    }

    /** The act-as header of the current request, or null (no request, or no header). */
    public static String actAsHeader() {
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes a) {
            String v = a.getRequest().getHeader(ACT_AS_HEADER);
            return v == null || v.isBlank() ? null : v.trim();
        }
        return null;
    }

    private ErpPrincipal fetch(String token, String actAs) {
        try {
            var spec = client.get()
                    .uri("/api/v1/me/authorities")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            if (actAs != null) {
                spec = spec.header(ACT_AS_HEADER, actAs);
            }
            ApiMessageDto<ErpPrincipal> body = spec
                    .retrieve()
                    .body(Nulls.nonNull(TYPE));
            if (body == null || body.getData() == null) {
                throw new AuthenticationServiceException("IAM returned no authorities");
            }
            return body.getData();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // IAM answered and refused (locked, suspended, bad token): final, never covered by the stale copy
            throw new AuthenticationServiceException("IAM refused the authority lookup", e);
        } catch (RestClientException e) {
            // connection refused, timeout, 5xx, unreadable answer
            throw new IamUnavailable(e);
        }
    }
}
