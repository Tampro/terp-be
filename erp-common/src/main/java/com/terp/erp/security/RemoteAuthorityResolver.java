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
 * <p>Fails closed: when IAM cannot be reached the request fails (500), it is never let through without authorities.
 * Not retried — a retry storm on IAM would make an outage worse.
 *
 * <p>Login as Company: the {@value #ACT_AS_HEADER} header of the request is passed on, so IAM resolves a super admin
 * as the company's administrator for that session; the cache is per token and header.
 */
public class RemoteAuthorityResolver implements AuthorityResolver {
    /** Session key of a super admin's "Login as Company" (docs/analysis/06 §3.2.1), checked by IAM. */
    public static final String ACT_AS_HEADER = "X-Act-As-Company";

    private static final ParameterizedTypeReference<ApiMessageDto<ErpPrincipal>> TYPE = new ParameterizedTypeReference<>() {
    };

    private final RestClient client;
    private final Cache<String, ErpPrincipal> cache;

    public RemoteAuthorityResolver(String iamBaseUrl, Duration ttl) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(3)));
        this.client = RestClient.builder().baseUrl(Nulls.nonNull(iamBaseUrl)).requestFactory(rf).build();
        this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(10_000).build();
    }

    @Override
    public ErpPrincipal resolve(Jwt jwt) {
        String actAs = actAsHeader();
        String token = jwt.getTokenValue();
        return cache.get(actAs == null ? token : token + "|" + actAs, k -> fetch(token, actAs));
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
        } catch (RestClientException e) {
            throw new AuthenticationServiceException("IAM authority lookup failed", e);
        }
    }
}
