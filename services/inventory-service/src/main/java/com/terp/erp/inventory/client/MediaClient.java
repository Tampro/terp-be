package com.terp.erp.inventory.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.terp.erp.common.Nulls;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.inventory.common.EInventoryError;
import com.terp.erp.inventory.config.InventoryProperties;
import com.terp.erp.security.RemoteAuthorityResolver;

import java.time.Duration;
import java.util.Objects;

/**
 * media-service as the caller (their token and language), so media-service applies its own permission and company
 * checks. Explicit timeouts, never retried: a failure is reported (INV_025), never hidden.
 */
@Slf4j
@Component
public class MediaClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaFile(String id, String companyId, String mimeType) {
        boolean image() {
            return mimeType != null && mimeType.toLowerCase().startsWith("image/");
        }
    }

    private final RestClient client;
    private final ObjectMapper mapper;

    public MediaClient(InventoryProperties props, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(10)));
        this.client = RestClient.builder().baseUrl(Nulls.nonNull(props.mediaServiceUrl())).requestFactory(rf).build();
        this.mapper = mapper;
    }

    /**
     * The file, when it is an image of companyId; otherwise INV_024 (400). media-service's own 4xx (not found,
     * another company, no permission) also becomes INV_024 so a foreign file id reveals nothing.
     */
    public MediaFile requireCompanyImage(Long fileId, Long companyId) {
        MediaFile f;
        try {
            JsonNode body = client.get().uri("/api/v1/media/files/{id}", fileId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + callerToken())
                    .headers(h -> {
                        String actAs = RemoteAuthorityResolver.actAsHeader();
                        if (actAs != null) {
                            h.set(RemoteAuthorityResolver.ACT_AS_HEADER, actAs);
                        }
                    })
                    .header(HttpHeaders.ACCEPT_LANGUAGE, LocaleContextHolder.getLocale().toLanguageTag())
                    .retrieve().body(JsonNode.class);
            f = body == null ? null : mapper.convertValue(body.path("data"), MediaFile.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw ApiException.badRequest(EInventoryError.INV_024, fileId);
            }
            log.error("media-service answered {}", e.getStatusCode().value());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EInventoryError.INV_025);
        } catch (RestClientException e) {
            log.error("media-service unreachable: {}", e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EInventoryError.INV_025);
        }
        if (f == null || !f.image() || !Objects.equals(f.companyId(), String.valueOf(companyId))) {
            throw ApiException.badRequest(EInventoryError.INV_024, fileId);
        }
        return f;
    }

    private static String callerToken() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        throw ApiException.unauthorized(ECommonError.COMMON_003);
    }
}
