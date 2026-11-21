package com.terp.erp.attendance.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.attendance.common.EAttendanceError;
import com.terp.erp.attendance.config.AttendanceProperties;
import com.terp.erp.exception.ApiException;

import java.util.Objects;

/** media-service, called as the caller (their token): leave attachments must be files of the company. */
@Component
public class MediaClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaFile(String id, String companyId, String originalName, String displayName, String mimeType,
                            long sizeBytes, String viewUrl, String downloadUrl) {
        public String name() {
            return displayName != null && !displayName.isBlank() ? displayName : originalName;
        }
    }

    private final CallerHttp http;
    private final ObjectMapper mapper;

    public MediaClient(AttendanceProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.mediaServiceUrl(), mapper, EAttendanceError.ATT_050, "media-service");
        this.mapper = mapper;
    }

    /** The file, checked against the company; 400 ATT_049 when it is not there. */
    public MediaFile require(Long fileId, Long companyId) {
        MediaFile f;
        try {
            f = fetch(fileId);
        } catch (RemoteApiException e) {
            if (e.getStatus().value() == 404) {
                throw ApiException.badRequest(EAttendanceError.ATT_049);
            }
            throw e;
        }
        if (f == null || !Objects.equals(f.companyId(), String.valueOf(companyId))) {
            throw ApiException.badRequest(EAttendanceError.ATT_049);
        }
        return f;
    }

    /** For display: a missing file or a caller without MEDIA_VIEW shows no link instead of failing the page. */
    public MediaFile find(Long fileId) {
        if (fileId == null) {
            return null;
        }
        try {
            return fetch(fileId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private MediaFile fetch(Long fileId) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/media/files/{id}", fileId)).path("data");
        return data.isMissingNode() || data.isNull() ? null : mapper.convertValue(data, MediaFile.class);
    }
}
