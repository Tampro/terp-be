package com.terp.erp.project.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import com.terp.erp.project.common.EProjectError;
import com.terp.erp.project.config.ProjectProperties;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;

import java.util.Objects;

/** media-service as the caller: an attachment must be a file of the record's company. */
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

    public MediaClient(ProjectProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.mediaServiceUrl(), mapper, EProjectError.PRJ_002, "media-service");
        this.mapper = mapper;
    }

    /** The file, when it belongs to companyId; otherwise `refused` (400). media-service's 404/403 become `refused`. */
    public MediaFile requireCompanyFile(Long fileId, Long companyId, ErrorCode refused) {
        MediaFile f;
        try {
            JsonNode data = http.call(c -> c.get().uri("/api/v1/media/files/{id}", fileId)).path("data");
            f = mapper.convertValue(data, MediaFile.class);
        } catch (RemoteApiException e) {
            throw ApiException.badRequest(refused);
        }
        if (f == null || !Objects.equals(f.companyId(), String.valueOf(companyId))) {
            throw ApiException.badRequest(refused);
        }
        return f;
    }
}
