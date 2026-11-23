package com.terp.erp.media.storage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.Nulls;
import com.terp.erp.exception.ApiException;
import com.terp.erp.media.config.MediaProperties;
import com.terp.erp.media.constant.EMediaError;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.RemoteAuthorityResolver;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.function.Function;

/**
 * Storage limit of a company's plan (user manual §4.1 "Storage Limit GB", -1 = unlimited), read from IAM
 * {@code GET /api/v1/me/company} with the caller's own token and kept for one minute per company. Failures are never
 * cached and refuse the upload (503 MEDIA_011): fail closed, like authority resolution.
 * TODO(assumption): fail closed when IAM is down (chosen over "allow and count later"); 1 GB = 1024^3 bytes.
 */
@Slf4j
@Component
public class StorageQuotaClient {
    /** Cached for "no limit": Caffeine keeps no null values. */
    static final long UNLIMITED = -1L;
    private static final long GIB = 1024L * 1024 * 1024;
    private static final ParameterizedTypeReference<ApiMessageDto<Remote>> TYPE = new ParameterizedTypeReference<>() {
    };

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Remote(String companyId, BigDecimal storageLimitGb) {
    }

    private final Cache<Long, Long> cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(10_000).build();
    private final Function<Long, BigDecimal> fetcher;

    // Two constructors (the other is for tests): Spring needs to be told which one to use.
    @Autowired
    public StorageQuotaClient(MediaProperties props) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(3)));
        RestClient client = RestClient.builder().baseUrl(Nulls.nonNull(props.iamServiceUrl())).requestFactory(rf)
                .build();
        this.fetcher = companyId -> fetch(client, companyId);
    }

    /** Tests: the IAM call replaced; it returns the plan's GB (null = unlimited) or throws when IAM is down. */
    StorageQuotaClient(Function<Long, BigDecimal> fetcher) {
        this.fetcher = fetcher;
    }

    /** Limit in bytes for the company, or null = unlimited (no plan, no limit, or a negative limit such as -1). */
    public Long limitBytes(long companyId) {
        Long cached = cache.getIfPresent(companyId);
        if (cached == null) {
            BigDecimal gb;
            try {
                gb = fetcher.apply(companyId);
            } catch (RestClientException | IllegalStateException e) {
                log.warn("Storage limit from IAM unavailable: {}", e.getClass().getSimpleName());
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EMediaError.MEDIA_011);
            }
            cached = gb == null || gb.signum() < 0 ? UNLIMITED : gb.multiply(BigDecimal.valueOf(GIB)).longValue();
            cache.put(companyId, cached);
        }
        return cached == UNLIMITED ? null : cached;
    }

    private static BigDecimal fetch(RestClient client, Long companyId) {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt)) {
            throw new IllegalStateException("no caller token");
        }
        // Only a super admin may name a company; IAM gives a company user their own one anyway.
        String uri = CurrentUser.get().superAdmin() ? "/api/v1/me/company?companyId=" + companyId : "/api/v1/me/company";
        ApiMessageDto<Remote> body = client.get().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getToken().getTokenValue())
                .headers(h -> {
                    String actAs = RemoteAuthorityResolver.actAsHeader();
                    if (actAs != null) {
                        h.set(RemoteAuthorityResolver.ACT_AS_HEADER, actAs);
                    }
                })
                .retrieve().body(Nulls.nonNull(TYPE));
        if (body == null || body.getData() == null || !String.valueOf(companyId).equals(body.getData().companyId())) {
            throw new IllegalStateException("unexpected IAM answer");
        }
        return body.getData().storageLimitGb();
    }
}
