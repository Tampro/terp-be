package com.terp.erp.iam.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.ai.AiPrompts.Prompt;
import com.terp.erp.iam.integration.CompanyIntegrationService.AiTarget;
import com.terp.erp.iam.integration.IntegrationDtos.EAiProvider;
import com.terp.erp.iam.integration.UrlGuard;
import com.terp.erp.iam.settings.ESettingsError;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.net.InetAddress;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiProviderClientTest {
    static final String KEY = "sk-test-SECRET-KEY-123";
    private static final Prompt PROMPT = new Prompt("SYSTEM-TEXT", "USER-TEXT", 800);

    private final ObjectMapper mapper = new ObjectMapper();
    private final AiHttp http = mock(AiHttp.class);
    private final UrlGuard guard = new UrlGuard(host -> new InetAddress[]{InetAddress.getByName("54.230.10.10")});
    private final AiProviderClient client = new AiProviderClient(http, guard, mapper);

    @SuppressWarnings("unchecked")
    private Sent send(EAiProvider provider, String model, int status, String answer) {
        when(http.postJson(any(), anyString(), anyMap())).thenReturn(new AiHttp.Response(status, answer));
        String text = client.complete(new AiTarget(provider, KEY, model), PROMPT);
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(http).postJson(uri.capture(), json.capture(), headers.capture());
        return new Sent(text, uri.getValue(), headers.getValue(), read(json.getValue()));
    }

    record Sent(String text, URI uri, Map<String, String> headers, JsonNode body) {
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void openAiRequestAndParsing() {
        Sent s = send(EAiProvider.OPENAI, "gpt-4o-mini", 200,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"  Draft text \"}}]}");
        assertThat(s.uri()).isEqualTo(URI.create("https://api.openai.com/v1/chat/completions"));
        assertThat(s.headers()).containsExactly(Map.entry("Authorization", "Bearer " + KEY));
        assertThat(s.body().path("model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(s.body().path("max_tokens").asInt()).isEqualTo(800);
        assertThat(s.body().path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(s.body().path("messages").get(0).path("content").asText()).isEqualTo("SYSTEM-TEXT");
        assertThat(s.body().path("messages").get(1).path("role").asText()).isEqualTo("user");
        assertThat(s.body().path("messages").get(1).path("content").asText()).isEqualTo("USER-TEXT");
        assertThat(s.body().toString()).doesNotContain(KEY);
        assertThat(s.text()).isEqualTo("Draft text");
    }

    @Test
    void anthropicRequestAndParsing() {
        Sent s = send(EAiProvider.ANTHROPIC, "claude-sonnet-4-5", 200,
                "{\"content\":[{\"type\":\"text\",\"text\":\"Hello \"},{\"type\":\"text\",\"text\":\"world\"}]}");
        assertThat(s.uri()).isEqualTo(URI.create("https://api.anthropic.com/v1/messages"));
        assertThat(s.headers()).containsEntry("x-api-key", KEY).containsEntry("anthropic-version", "2023-06-01")
                .doesNotContainKey("Authorization");
        assertThat(s.body().path("model").asText()).isEqualTo("claude-sonnet-4-5");
        assertThat(s.body().path("max_tokens").asInt()).isEqualTo(800);
        assertThat(s.body().path("system").asText()).isEqualTo("SYSTEM-TEXT");
        assertThat(s.body().path("messages")).hasSize(1);
        assertThat(s.body().path("messages").get(0).path("role").asText()).isEqualTo("user");
        assertThat(s.body().path("messages").get(0).path("content").asText()).isEqualTo("USER-TEXT");
        assertThat(s.text()).isEqualTo("Hello world");
    }

    @Test
    void googleRequestKeepsTheKeyOutOfTheUrl() {
        Sent s = send(EAiProvider.GOOGLE, "gemini-2.0-flash", 200,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Xin chào\"}]}}]}");
        assertThat(s.uri()).isEqualTo(URI.create(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"));
        assertThat(s.uri().toString()).doesNotContain(KEY).doesNotContain("key=");
        assertThat(s.headers()).containsExactly(Map.entry("x-goog-api-key", KEY));
        assertThat(s.body().path("systemInstruction").path("parts").get(0).path("text").asText())
                .isEqualTo("SYSTEM-TEXT");
        assertThat(s.body().path("contents").get(0).path("parts").get(0).path("text").asText())
                .isEqualTo("USER-TEXT");
        assertThat(s.body().path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(800);
        assertThat(s.text()).isEqualTo("Xin chào");
    }

    @Test
    void errorMapping() {
        assertError(401, "{\"error\":{\"code\":\"invalid_api_key\",\"message\":\"Incorrect key " + KEY + "\"}}",
                HttpStatus.BAD_REQUEST, EAiError.AI_005, "401 invalid_api_key");
        assertError(403, "{}", HttpStatus.BAD_REQUEST, EAiError.AI_005, "403");
        assertError(429, "{\"error\":{\"type\":\"rate_limit_error\"}}", HttpStatus.TOO_MANY_REQUESTS,
                EAiError.AI_006, null);
        assertError(500, "<html>boom</html>", HttpStatus.SERVICE_UNAVAILABLE, EAiError.AI_007, "500");
        assertError(529, "{\"error\":{\"type\":\"overloaded_error\"}}", HttpStatus.SERVICE_UNAVAILABLE,
                EAiError.AI_007, "529 overloaded_error");
        assertError(404, "{\"error\":{\"status\":\"NOT_FOUND\",\"message\":\"model x\"}}", HttpStatus.BAD_REQUEST,
                EAiError.AI_009, "404 NOT_FOUND");
        assertError(200, "not json", HttpStatus.BAD_GATEWAY, EAiError.AI_008, null);
        assertError(200, "{\"choices\":[]}", HttpStatus.BAD_GATEWAY, EAiError.AI_008, null);
        assertError(200, null, HttpStatus.BAD_GATEWAY, EAiError.AI_008, null);
    }

    private void assertError(int status, String body, HttpStatus http, EAiError code, String arg) {
        AiHttp h = mock(AiHttp.class);
        when(h.postJson(any(), anyString(), anyMap())).thenReturn(new AiHttp.Response(status, body));
        AiProviderClient c = new AiProviderClient(h, guard, mapper);
        assertThatThrownBy(() -> c.complete(new AiTarget(EAiProvider.OPENAI, KEY, "gpt-4o-mini"), PROMPT))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(http);
                    assertThat(e.getErrorCode()).isEqualTo(code);
                    if (arg != null) {
                        assertThat(e.getArgs()).containsExactly(arg);
                    }
                    // The provider's body (which may echo the key or the user's text) is never passed on.
                    assertThat(java.util.Arrays.toString(e.getArgs())).doesNotContain(KEY).doesNotContain("boom");
                });
    }

    @Test
    void timeoutIsUnavailableWithoutRetry() {
        when(http.postJson(any(), anyString(), anyMap())).thenThrow(new AiHttp.TransportException("TIMEOUT"));
        assertThatThrownBy(() -> client.complete(new AiTarget(EAiProvider.ANTHROPIC, KEY, "claude-x"), PROMPT))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getErrorCode()).isEqualTo(EAiError.AI_007);
                    assertThat(e.getArgs()).containsExactly("TIMEOUT");
                });
        verify(http, org.mockito.Mockito.times(1)).postJson(any(), anyString(), anyMap());
    }

    @Test
    void badModelNameNeverCalls() {
        assertThatThrownBy(() -> client.complete(new AiTarget(EAiProvider.GOOGLE, KEY, "../x?key=1"), PROMPT))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(EAiError.AI_010));
        verify(http, never()).postJson(any(), anyString(), anyMap());
    }

    @Test
    void privateAddressIsRefusedBeforeTheCall() {
        UrlGuard internal = new UrlGuard(host -> new InetAddress[]{InetAddress.getByName("10.0.0.5")});
        AiProviderClient c = new AiProviderClient(http, internal, mapper);
        assertThatThrownBy(() -> c.complete(new AiTarget(EAiProvider.OPENAI, KEY, "gpt-4o"), PROMPT))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ESettingsError.SETTINGS_013));
        verify(http, never()).postJson(any(), anyString(), anyMap());
    }

    @Test
    void toStringsHideSecretsAndText() {
        AiTarget t = new AiTarget(EAiProvider.OPENAI, KEY, "gpt-4o");
        assertThat(t.toString()).doesNotContain(KEY);
        AiProviderClient.Call call = client.build(t, PROMPT);
        assertThat(call.toString()).doesNotContain(KEY).doesNotContain("USER-TEXT");
        assertThat(PROMPT.toString()).doesNotContain("USER-TEXT").doesNotContain("SYSTEM-TEXT");
        assertThat(new AiHttp.Response(200, "secret body").toString()).doesNotContain("secret body");
    }
}
