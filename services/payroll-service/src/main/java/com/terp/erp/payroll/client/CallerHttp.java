package com.terp.erp.payroll.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.terp.erp.common.Nulls;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.exception.ErrorCode;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Calls another ERP service as the caller (their own token, their language), so that service applies its own
 * permission and company checks: no service credential exists. Explicit timeouts; never retried, so a failure is
 * reported, never hidden behind a stale answer.
 */
@Slf4j
class CallerHttp {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final ErrorCode unavailable;
    private final String name;
    private final Supplier<String> tokenSource;
    /** Calls made as the caller pass Login as Company on; a service account never acts as a company. */
    private final boolean asCaller;

    CallerHttp(String baseUrl, ObjectMapper mapper, ErrorCode unavailable, String name) {
        this(baseUrl, mapper, unavailable, name, CallerHttp::callerToken, true);
    }

    /** tokenSource = the service account instead of the caller (see ServiceTokenProvider). */
    CallerHttp(String baseUrl, ObjectMapper mapper, ErrorCode unavailable, String name, Supplier<String> tokenSource) {
        this(baseUrl, mapper, unavailable, name, tokenSource, false);
    }

    private CallerHttp(String baseUrl, ObjectMapper mapper, ErrorCode unavailable, String name,
                       Supplier<String> tokenSource, boolean asCaller) {
        this.asCaller = asCaller;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
        rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(10)));
        this.client = RestClient.builder().baseUrl(Nulls.nonNull(baseUrl)).requestFactory(rf).build();
        this.mapper = mapper;
        this.unavailable = unavailable;
        this.name = name;
        this.tokenSource = tokenSource;
    }

    /** 4xx with the ERP envelope: RemoteApiException (relayed as is). Anything else: 503 of this module. */
    JsonNode call(Function<RestClient, RestClient.RequestHeadersSpec<?>> request) {
        try {
            RestClient.RequestHeadersSpec<?> spec = request.apply(client)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenSource.get())
                    .headers(h -> {
                        String actAs = asCaller ? com.terp.erp.security.RemoteAuthorityResolver.actAsHeader() : null;
                        if (actAs != null) {
                            h.set(com.terp.erp.security.RemoteAuthorityResolver.ACT_AS_HEADER, actAs);
                        }
                    })
                    .header(HttpHeaders.ACCEPT_LANGUAGE, LocaleContextHolder.getLocale().toLanguageTag());
            JsonNode body = spec.retrieve().body(JsonNode.class);
            return body == null ? mapper.createObjectNode() : body;
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                JsonNode body = readBody(e);
                String code = body.path("code").asText("");
                if (!code.isEmpty()) {
                    throw new RemoteApiException(e.getStatusCode(), code, body.path("message").asText(code));
                }
                if (e.getStatusCode().value() == 403) {
                    throw ApiException.forbidden(ECommonError.COMMON_004);
                }
            }
            log.error("{} answered {}", name, e.getStatusCode().value());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, unavailable);
        } catch (RestClientException e) {
            log.error("{} unreachable: {}", name, e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, unavailable);
        }
    }

    private JsonNode readBody(HttpStatusCodeException e) {
        try {
            return mapper.readTree(e.getResponseBodyAsString());
        } catch (Exception ignored) {
            return mapper.createObjectNode();
        }
    }

    private static String callerToken() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        throw ApiException.unauthorized(ECommonError.COMMON_003);
    }
}
