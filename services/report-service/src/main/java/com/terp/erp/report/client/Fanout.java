package com.terp.erp.report.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Calls several ERP services at once as the caller (their own token and language), so each service applies its
 * own permission and company checks. A failing part never fails the whole answer and is never filled with made-up
 * numbers: it comes back as data = null with the reason (the service's error code, or UNAVAILABLE).
 */
@Slf4j
@Component
public class Fanout {
    public static final String UNAVAILABLE = "UNAVAILABLE";

    @Schema(description = "One part of the answer: data, or null with the reason (e.g. COMMON_004 = no permission)")
    public record Part(JsonNode data, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    /** One call: the base URL of the service and the path with its query string. */
    public record Call(String baseUrl, String uri) {
    }

    private final ObjectMapper mapper;
    private final ExecutorService pool = Executors.newFixedThreadPool(16);
    private final Map<String, RestClient> clients = new java.util.concurrent.ConcurrentHashMap<>();

    public Fanout(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Runs every call in parallel and returns the parts under the same keys, in order. */
    public Map<String, Part> all(Map<String, Call> calls) {
        String token = callerToken();
        String lang = LocaleContextHolder.getLocale().toLanguageTag();
        String actAs = com.terp.erp.security.RemoteAuthorityResolver.actAsHeader();
        Map<String, CompletableFuture<Part>> running = new LinkedHashMap<>();
        calls.forEach((key, call) -> running.put(key,
                CompletableFuture.supplyAsync(() -> one(call, token, lang, actAs), pool)));
        Map<String, Part> out = new LinkedHashMap<>();
        running.forEach((key, f) -> {
            try {
                out.put(key, f.get(15, TimeUnit.SECONDS));
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                out.put(key, new Part(null, UNAVAILABLE));
            }
        });
        return out;
    }

    Part one(Call call, String token, String lang, String actAs) {
        try {
            JsonNode body = client(call.baseUrl()).get().uri(call.uri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, lang)
                    .headers(h -> {
                        if (actAs != null) {
                            h.set(com.terp.erp.security.RemoteAuthorityResolver.ACT_AS_HEADER, actAs);
                        }
                    })
                    .retrieve().body(JsonNode.class);
            JsonNode data = body == null ? null : body.get("data");
            return new Part(data == null || data.isNull() ? null : data, null);
        } catch (HttpStatusCodeException e) {
            String code = errorCode(e);
            log.info("{} answered {} ({})", path(call.uri()), e.getStatusCode().value(), code);
            return new Part(null, code);
        } catch (RestClientException e) {
            log.warn("{} unreachable: {}", path(call.uri()), e.getClass().getSimpleName());
            return new Part(null, UNAVAILABLE);
        }
    }

    private String errorCode(HttpStatusCodeException e) {
        try {
            String code = mapper.readTree(e.getResponseBodyAsString()).path("code").asText("");
            if (!code.isEmpty()) {
                return code;
            }
        } catch (Exception ignored) {
            // not the ERP envelope
        }
        return e.getStatusCode().value() == 403 ? ECommonError.COMMON_004.name() : UNAVAILABLE;
    }

    private RestClient client(String baseUrl) {
        return clients.computeIfAbsent(baseUrl, url -> {
            SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
            rf.setConnectTimeout(Nulls.nonNull(Duration.ofSeconds(2)));
            rf.setReadTimeout(Nulls.nonNull(Duration.ofSeconds(8)));
            return RestClient.builder().baseUrl(Nulls.nonNull(url)).requestFactory(rf).build();
        });
    }

    /** The path without the query string: ids and dates in the query are not logged. */
    private static String path(String uri) {
        int q = uri.indexOf('?');
        return q < 0 ? uri : uri.substring(0, q);
    }

    private static String callerToken() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        throw ApiException.unauthorized(ECommonError.COMMON_003);
    }

    /** Converts a part's data; null when the part failed. */
    public <T> T read(Part p, Class<T> type) {
        return p == null || p.data() == null ? null : mapper.convertValue(p.data(), type);
    }

    public <T> T map(Part p, Function<JsonNode, T> f) {
        return p == null || p.data() == null ? null : f.apply(p.data());
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
