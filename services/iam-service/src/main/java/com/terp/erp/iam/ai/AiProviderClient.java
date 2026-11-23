package com.terp.erp.iam.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.ai.AiPrompts.Prompt;
import com.terp.erp.iam.integration.CompanyIntegrationService.AiTarget;
import com.terp.erp.iam.integration.IntegrationDtos.EAiProvider;
import com.terp.erp.iam.integration.UrlGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Calls the company's AI provider. Hosts are fixed per provider and checked by UrlGuard (https, public address)
 * right before the call; the key goes in a header only, never in the URL (the reference puts the Google key in the
 * query string; not copied). A provider error is reduced to its HTTP status and short error code: the provider's
 * body is never returned or logged.
 */
@Component
@RequiredArgsConstructor
public class AiProviderClient {
    static final String OPENAI_HOST = "api.openai.com";
    static final String ANTHROPIC_HOST = "api.anthropic.com";
    static final String GOOGLE_HOST = "generativelanguage.googleapis.com";
    static final String ANTHROPIC_VERSION = "2023-06-01";
    /** Model names of the three providers (gpt-4o-mini, claude-…, gemini-…); also keeps the Google URL path safe. */
    static final Pattern MODEL = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}");
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    /** The exact request, built before any network so tests can check it. toString never shows headers or body. */
    record Call(URI uri, Map<String, String> headers, String json) {
        @Override
        public String toString() {
            return "Call[" + uri.getHost() + "]";
        }
    }

    private final AiHttp http;
    private final UrlGuard guard;
    private final ObjectMapper mapper;

    static boolean validModel(String model) {
        return model != null && MODEL.matcher(model).matches();
    }

    /** The generated text; ApiException AI_005…AI_010 otherwise. */
    public String complete(AiTarget t, Prompt p) {
        Call call = build(t, p);
        URI checked = guard.check(call.uri().toString(), call.uri().getHost());
        AiHttp.Response r;
        try {
            r = http.postJson(checked, call.json(), call.headers());
        } catch (AiHttp.TransportException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EAiError.AI_007, e.reason());
        }
        int s = r.status();
        if (s >= 200 && s < 300) {
            return parse(t.provider(), r.body());
        }
        String code = s + errorCode(r.body()).map(c -> " " + c).orElse("");
        if (s == 401 || s == 403) {
            throw ApiException.badRequest(EAiError.AI_005, code);
        }
        if (s == 429) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, EAiError.AI_006);
        }
        if (s >= 500) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EAiError.AI_007, code);
        }
        throw ApiException.badRequest(EAiError.AI_009, code);
    }

    Call build(AiTarget t, Prompt p) {
        if (!validModel(t.model())) {
            throw ApiException.badRequest(EAiError.AI_010);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        ObjectNode body = mapper.createObjectNode();
        URI uri;
        switch (t.provider()) {
            case OPENAI -> {
                uri = URI.create("https://" + OPENAI_HOST + "/v1/chat/completions");
                headers.put("Authorization", "Bearer " + t.apiKey());
                body.put("model", t.model());
                ArrayNode messages = body.putArray("messages");
                messages.addObject().put("role", "system").put("content", p.system());
                messages.addObject().put("role", "user").put("content", p.user());
                body.put("max_tokens", p.maxTokens());
            }
            case ANTHROPIC -> {
                uri = URI.create("https://" + ANTHROPIC_HOST + "/v1/messages");
                headers.put("x-api-key", t.apiKey());
                headers.put("anthropic-version", ANTHROPIC_VERSION);
                body.put("model", t.model());
                body.put("max_tokens", p.maxTokens());
                body.put("system", p.system());
                body.putArray("messages").addObject().put("role", "user").put("content", p.user());
            }
            case GOOGLE -> {
                uri = URI.create("https://" + GOOGLE_HOST + "/v1beta/models/" + t.model() + ":generateContent");
                headers.put("x-goog-api-key", t.apiKey());
                body.putObject("systemInstruction").putArray("parts").addObject().put("text", p.system());
                ObjectNode content = body.putArray("contents").addObject();
                content.put("role", "user");
                content.putArray("parts").addObject().put("text", p.user());
                body.putObject("generationConfig").put("maxOutputTokens", p.maxTokens());
            }
            default -> throw ApiException.badRequest(EAiError.AI_003);
        }
        return new Call(uri, headers, body.toString());
    }

    String parse(EAiProvider provider, String body) {
        JsonNode n = readTree(body);
        StringBuilder out = new StringBuilder();
        switch (provider) {
            case OPENAI -> out.append(n.path("choices").path(0).path("message").path("content").asText(""));
            case ANTHROPIC -> n.path("content").forEach(part -> {
                if ("text".equals(part.path("type").asText())) {
                    out.append(part.path("text").asText(""));
                }
            });
            case GOOGLE -> n.path("candidates").path(0).path("content").path("parts")
                    .forEach(part -> out.append(part.path("text").asText("")));
            default -> throw badResponse();
        }
        String text = out.toString().trim();
        if (text.isEmpty()) {
            throw badResponse();
        }
        return text;
    }

    private JsonNode readTree(String body) {
        if (body == null) {
            throw badResponse();
        }
        try {
            JsonNode n = mapper.readTree(body);
            if (n == null || !n.isObject()) {
                throw badResponse();
            }
            return n;
        } catch (JsonProcessingException e) {
            throw badResponse();
        }
    }

    private static ApiException badResponse() {
        return new ApiException(HttpStatus.BAD_GATEWAY, EAiError.AI_008);
    }

    /** The provider's short error code (OpenAI error.code / Anthropic error.type / Google error.status), if any. */
    private Optional<String> errorCode(String body) {
        if (body == null) {
            return Optional.empty();
        }
        try {
            JsonNode e = mapper.readTree(body).path("error");
            for (String f : new String[]{"code", "type", "status"}) {
                String v = e.path(f).asText("");
                if (ERROR_CODE.matcher(v).matches()) {
                    return Optional.of(v);
                }
            }
        } catch (JsonProcessingException | RuntimeException e) {
            // an unreadable error body: the HTTP status alone is reported
        }
        return Optional.empty();
    }
}
