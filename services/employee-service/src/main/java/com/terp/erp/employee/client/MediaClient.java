package com.terp.erp.employee.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.config.EmployeeProperties;
import com.terp.erp.exception.ApiException;

import java.time.Duration;
import java.util.Objects;

/**
 * Profile images and documents are files of the media library; this service keeps only their id. Looking a file up
 * through media-service with the caller's token is also the ownership check: a file of another company answers 404.
 */
@Component
public class MediaClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaFile(String id, String companyId, String originalName, String displayName, String mimeType,
                            String kind, long sizeBytes, String viewUrl, String downloadUrl) {
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;
    // Links are valid 10 minutes; reuse them for 5 so a list of 20 photos is not 20 calls on every page.
    private final Cache<Long, MediaFile> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5)).maximumSize(5_000).build();

    private final CallerHttp internal;

    public MediaClient(EmployeeProperties props, ObjectMapper mapper, ServiceTokenProvider serviceToken) {
        this.http = new CallerHttp(props.mediaServiceUrl(), mapper, EEmployeeError.EMP_015, "media-service");
        this.internal = new CallerHttp(props.mediaServiceUrl(), mapper, EEmployeeError.EMP_015, "media-service",
                serviceToken::token);
        this.mapper = mapper;
    }

    /** The file, checked against the employee's company; 400 EMP_012 when it is not there. */
    public MediaFile require(Long fileId, Long companyId) {
        MediaFile f;
        try {
            f = fetch(fileId);
        } catch (RemoteApiException e) {
            if (e.getStatus().value() == 404) {
                throw ApiException.badRequest(EEmployeeError.EMP_012);
            }
            throw e;
        }
        // A super admin sees every company's files: the file must still belong to the employee's company.
        if (f == null || !Objects.equals(f.companyId(), String.valueOf(companyId))) {
            throw ApiException.badRequest(EEmployeeError.EMP_012);
        }
        cache.put(fileId, f);
        return f;
    }

    /** For display: a missing or unreachable file shows as no file instead of failing the page. */
    public MediaFile find(Long fileId) {
        if (fileId == null) {
            return null;
        }
        MediaFile hit = cache.getIfPresent(fileId);
        if (hit != null) {
            return hit;
        }
        try {
            MediaFile f = fetch(fileId);
            if (f != null) {
                cache.put(fileId, f);
            }
            return f;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Signed links of a company file through employee-service's service account, for a caller who may not open the
     * media library. Only after the caller was authorized for the file here (announcement audience). Not cached:
     * the cache above is filled by callers with MEDIA_VIEW. Null when the file is gone or media is unreachable.
     */
    public MediaFile linksAsService(Long fileId, Long companyId) {
        try {
            JsonNode data = internal.call(c -> c.get().uri("/api/v1/internal/media/files/{id}?companyId={c}", fileId,
                    companyId)).path("data");
            return data.isMissingNode() || data.isNull() ? null : mapper.convertValue(data, MediaFile.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private MediaFile fetch(Long fileId) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/media/files/{id}", fileId)).path("data");
        return data.isMissingNode() || data.isNull() ? null : mapper.convertValue(data, MediaFile.class);
    }
}
