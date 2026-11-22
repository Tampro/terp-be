package com.terp.erp.support.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.exception.ApiException;
import com.terp.erp.support.common.ESupportError;
import com.terp.erp.support.config.SupportProperties;

import java.util.Objects;

/**
 * media-service as the caller: an attachment must be a file of the caller's company. Needs a token, so public portal
 * submissions never attach files (see PublicSupportService).
 */
@Component
public class MediaClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaFile(String id, String companyId, String displayName, String originalName, String mimeType,
                            long sizeBytes) {
        public String name() {
            return displayName == null || displayName.isBlank() ? originalName : displayName;
        }
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public MediaClient(SupportProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.mediaServiceUrl(), mapper, ESupportError.SUP_002, "media-service");
        this.mapper = mapper;
    }

    /** The file when it belongs to companyId; otherwise SUP_007 (400). media-service's 404/403 become SUP_007 too. */
    public MediaFile requireCompanyFile(Long fileId, Long companyId) {
        MediaFile f;
        try {
            JsonNode data = http.call(c -> c.get().uri("/api/v1/media/files/{id}", fileId)).path("data");
            f = mapper.convertValue(data, MediaFile.class);
        } catch (RemoteApiException e) {
            throw ApiException.badRequest(ESupportError.SUP_007, fileId);
        }
        if (f == null || !Objects.equals(f.companyId(), String.valueOf(companyId))) {
            throw ApiException.badRequest(ESupportError.SUP_007, fileId);
        }
        return f;
    }
}
