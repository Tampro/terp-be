package com.terp.erp.media.storage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.Nulls;
import com.terp.erp.media.config.MediaProperties;
import com.terp.erp.media.constant.EStorageDriver;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Upload limits set by the super admin (IAM Settings → Storage): allowed extensions and max size. Read from IAM
 * with the caller's own token and kept for one minute. If IAM cannot be reached, the last known policy is used, or
 * the service's own defaults (all types it can verify, MEDIA_MAX_FILE_SIZE) — never "anything goes": FileTypePolicy
 * and the service's size cap always apply on top.
 */
@Slf4j
@Component
public class StoragePolicyClient {
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final ParameterizedTypeReference<ApiMessageDto<Remote>> TYPE = new ParameterizedTypeReference<>() {
    };

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Remote(List<String> allowedFileTypes, int maxUploadSizeKb, String driver) {
    }

    /**
     * null allowedTypes = no extra restriction beyond FileTypePolicy. null driver = not known (IAM unreachable and
     * nothing cached): StorageRouter then decides, never silently local when S3 is configured.
     */
    public record Policy(Set<String> allowedTypes, long maxBytes, EStorageDriver driver) {
        public Policy(Set<String> allowedTypes, long maxBytes) {
            this(allowedTypes, maxBytes, null);
        }
    }

    private final RestClient client;
    private final MediaProperties props;
    private final Clock clock;
    private volatile Policy cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public StoragePolicyClient(MediaProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(3)));
        this.client = RestClient.builder().baseUrl(Nulls.nonNull(props.iamServiceUrl())).requestFactory(rf).build();
    }

    public Policy current() {
        Policy p = cached;
        if (p != null && clock.instant().isBefore(cachedAt.plus(TTL))) {
            return p;
        }
        Policy fresh = fetch();
        if (fresh != null) {
            cached = fresh;
            cachedAt = clock.instant();
            return fresh;
        }
        return p != null ? p : new Policy(null, props.maxFileSize().toBytes());
    }

    private Policy fetch() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt)) {
            return null;
        }
        try {
            ApiMessageDto<Remote> body = client.get().uri("/api/v1/settings/storage")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getToken().getTokenValue())
                    .headers(h -> {
                        String actAs = com.terp.erp.security.RemoteAuthorityResolver.actAsHeader();
                        if (actAs != null) {
                            h.set(com.terp.erp.security.RemoteAuthorityResolver.ACT_AS_HEADER, actAs);
                        }
                    })
                    .retrieve().body(Nulls.nonNull(TYPE));
            if (body == null || body.getData() == null) {
                return null;
            }
            Remote r = body.getData();
            long bytes = Math.min((long) r.maxUploadSizeKb() * 1024, props.maxFileSize().toBytes());
            return new Policy(r.allowedFileTypes().stream().map(t -> t.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet()), bytes, driverOf(r.driver()));
        } catch (RestClientException e) {
            log.warn("Storage policy from IAM unavailable, using the last known one: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /** Settings saved before the driver existed have none: they were local. */
    static EStorageDriver driverOf(String value) {
        if (value == null || value.isBlank()) {
            return EStorageDriver.LOCAL;
        }
        try {
            return EStorageDriver.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown storage driver from IAM, treated as not known");
            return null;
        }
    }
}
