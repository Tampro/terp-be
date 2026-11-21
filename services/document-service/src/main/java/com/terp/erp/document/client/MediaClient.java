package com.terp.erp.document.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.terp.erp.document.common.EDocumentError;
import com.terp.erp.document.config.DocumentProperties;
import com.terp.erp.exception.ApiException;

import java.util.Objects;

/** media-service, called as the caller (their token): HR document files must be files of the company. */
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

    public MediaClient(DocumentProperties props, ObjectMapper mapper) {
        this.http = new CallerHttp(props.mediaServiceUrl(), mapper, EDocumentError.DOC_007, "media-service");
        this.mapper = mapper;
    }

    /** The file, checked against the company; 400 DOC_008 when it is not there. */
    public MediaFile require(Long fileId, Long companyId) {
        MediaFile f;
        try {
            f = fetch(fileId);
        } catch (RemoteApiException e) {
            if (e.getStatus().value() == 404) {
                throw ApiException.badRequest(EDocumentError.DOC_008);
            }
            throw e;
        }
        if (f == null || !Objects.equals(f.companyId(), String.valueOf(companyId))) {
            throw ApiException.badRequest(EDocumentError.DOC_008);
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

    /** Uploads a generated file to the company's media library as the caller (MEDIA_UPLOAD there); type from the name. */
    public MediaFile upload(Long companyId, String fileName, byte[] bytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        JsonNode data = http.call(c -> c.post().uri(u -> u.path("/api/v1/media/files")
                .queryParam("companyId", companyId).build()).contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)).path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, EDocumentError.DOC_007);
        }
        return mapper.convertValue(data.get(0), MediaFile.class);
    }

    private MediaFile fetch(Long fileId) {
        JsonNode data = http.call(c -> c.get().uri("/api/v1/media/files/{id}", fileId)).path("data");
        return data.isMissingNode() || data.isNull() ? null : mapper.convertValue(data, MediaFile.class);
    }
}
