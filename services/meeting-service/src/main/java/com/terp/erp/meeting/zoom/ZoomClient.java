package com.terp.erp.meeting.zoom;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.terp.erp.common.Nulls;
import com.terp.erp.exception.ApiException;
import com.terp.erp.meeting.common.EMeetingError;
import com.terp.erp.meeting.config.MeetingProperties;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;

/**
 * Zoom REST API with Server-to-Server OAuth (account_credentials grant). Explicit timeouts; never retried, so a
 * failure is reported (MTG_024) and nothing pretends success. Access tokens are cached per company for a little less
 * than their lifetime; tokens and secrets are never logged.
 */
@Slf4j
@Component
public class ZoomClient {
    /** Zoom tokens live one hour; refresh a few minutes early. */
    private static final Duration TOKEN_TTL = Duration.ofMinutes(55);

    private final RestClient client;
    private final MeetingProperties.Zoom urls;
    private final Cache<String, String> tokens = Caffeine.newBuilder().expireAfterWrite(TOKEN_TTL)
            .maximumSize(10_000).build();

    /** Plain credentials, only ever in memory. */
    public record Credentials(Long companyId, String accountId, String clientId, String clientSecret) {
        String cacheKey() {
            return companyId + ":" + accountId + ":" + clientId + ":" + clientSecret.hashCode();
        }
    }

    public ZoomClient(MeetingProperties props) {
        // The JDK HttpClient: HttpURLConnection (SimpleClientHttpRequestFactory) cannot send PATCH, which Zoom needs.
        HttpClient http = HttpClient.newBuilder().connectTimeout(Nulls.nonNull(Duration.ofSeconds(3))).build();
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(http);
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(10)));
        this.client = RestClient.builder().requestFactory(rf).build();
        this.urls = props.zoom();
    }

    /** Test Connection: a fresh token (cache bypassed). MTG_023 when Zoom refuses the credentials. */
    public void test(Credentials c) {
        tokens.put(c.cacheKey(), requestToken(c));
    }

    public JsonNode post(Credentials c, String path, Map<String, Object> body) {
        return call(c, r -> r.post().uri(urls.apiUrl() + path).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    public void patch(Credentials c, String path, Map<String, Object> body) {
        call(c, r -> r.patch().uri(urls.apiUrl() + path).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    /** true = deleted, false = Zoom no longer had it (404). */
    public boolean delete(Credentials c, String path) {
        try {
            call(c, r -> r.delete().uri(urls.apiUrl() + path));
            return true;
        } catch (ZoomNotFound e) {
            return false;
        }
    }

    /** Zoom answered 404 for the resource. */
    public static class ZoomNotFound extends RuntimeException {
        ZoomNotFound() {
            super("zoom 404", null, false, false);
        }
    }

    private JsonNode call(Credentials c, Function<RestClient, RestClient.RequestHeadersSpec<?>> request) {
        String token = tokens.get(c.cacheKey(), k -> requestToken(c));
        try {
            JsonNode body = request.apply(client).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve().body(JsonNode.class);
            return body;
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 401) {
                tokens.invalidate(c.cacheKey());
            }
            if (e.getStatusCode().value() == 404) {
                throw new ZoomNotFound();
            }
            log.error("Zoom API answered {}", e.getStatusCode().value());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EMeetingError.MTG_024);
        } catch (RestClientException e) {
            log.error("Zoom API unreachable: {}", e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EMeetingError.MTG_024);
        }
    }

    private String requestToken(Credentials c) {
        String basic = Base64.getEncoder().encodeToString((c.clientId() + ":" + c.clientSecret())
                .getBytes(StandardCharsets.UTF_8));
        try {
            JsonNode body = client.post().uri(urls.oauthUrl() + "?grant_type=account_credentials&account_id={id}",
                            c.accountId())
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic).retrieve().body(JsonNode.class);
            String token = body == null ? "" : body.path("access_token").asText("");
            if (token.isEmpty()) {
                throw ApiException.badRequest(EMeetingError.MTG_023);
            }
            return token;
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw ApiException.badRequest(EMeetingError.MTG_023);
            }
            log.error("Zoom OAuth answered {}", e.getStatusCode().value());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EMeetingError.MTG_024);
        } catch (RestClientException e) {
            log.error("Zoom OAuth unreachable: {}", e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EMeetingError.MTG_024);
        }
    }
}
