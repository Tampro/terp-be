package com.terp.erp.iam.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Company integrations (ERP manual 19; reference keys slack_webhook_url, telegram_bot_token / telegram_chat_id,
 * twilio_sid / twilio_token / twilio_from, ai_provider / ai_api_key / ai_model, google_calendar_id /
 * google_calendar_json_file). Three shapes each: Stored (secret encrypted, never leaves the service), Form (secret
 * write-only: blank or omitted keeps the stored one) and View (only whether a secret is set).
 */
public final class IntegrationDtos {
    private IntegrationDtos() {
    }

    /** Path names of DELETE /api/v1/settings/integrations/{type}; section = the company_setting row. */
    public enum EIntegration {
        SLACK("slack", "INT_SLACK"), TELEGRAM("telegram", "INT_TELEGRAM"), TWILIO("twilio", "INT_TWILIO"),
        AI_ASSISTANT("ai-assistant", "INT_AI"), GOOGLE_CALENDAR("google-calendar", "INT_GCAL");

        public final String path;
        public final String section;

        EIntegration(String path, String section) {
            this.path = path;
            this.section = section;
        }
    }

    /** Reference AIAssistantSettingsController: in:openai,anthropic,google. */
    public enum EAiProvider { OPENAI, ANTHROPIC, GOOGLE }

    // ------------------------------------------------------------------ stored

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SlackStored(boolean enabled, String webhookUrlEnc) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramStored(boolean enabled, String botTokenEnc, String chatId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TwilioStored(boolean enabled, String accountSid, String authTokenEnc, String fromNumber) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiStored(boolean enabled, EAiProvider provider, String apiKeyEnc, String model) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoogleCalendarStored(boolean enabled, String calendarId, String serviceAccountJsonEnc,
                                       String serviceAccountEmail) {
    }

    // ------------------------------------------------------------------ forms (PUT = full replacement except secrets)

    public record SlackForm(
            @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean enabled,
            @Schema(description = "Write-only. https://hooks.slack.com/...; blank/omitted keeps the stored one",
                    example = "https://hooks.slack.com/services/T000/B000/XXXX") @Size(max = 500) String webhookUrl) {
    }

    public record TelegramForm(
            @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean enabled,
            @Schema(description = "Write-only; blank/omitted keeps the stored one", example = "123456:ABC-DEF_ghi")
            @Size(max = 100) @Pattern(regexp = "^$|[0-9]{3,20}:[A-Za-z0-9_-]{20,80}") String botToken,
            @Schema(example = "-1001234567890") @Size(max = 64) @Pattern(regexp = "^$|-?[0-9]{1,20}|@[A-Za-z0-9_]{4,32}")
            String chatId) {
    }

    public record TwilioForm(
            @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean enabled,
            @Schema(example = "AC00000000000000000000000000000000") @Size(max = 34) @Pattern(regexp = "^$|AC[0-9a-fA-F]{32}")
            String accountSid,
            @Schema(description = "Write-only; blank/omitted keeps the stored one") @Size(max = 100) String authToken,
            @Schema(description = "E.164", example = "+15005550006") @Size(max = 20) @Pattern(regexp = "^$|\\+[1-9][0-9]{6,14}")
            String fromNumber) {
    }

    public record AiForm(
            @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean enabled,
            @Schema(example = "OPENAI") EAiProvider provider,
            @Schema(description = "Write-only; blank/omitted keeps the stored one") @Size(max = 500) String apiKey,
            @Schema(example = "gpt-4o-mini") @Size(max = 100) @Pattern(regexp = "^$|[A-Za-z0-9._:/-]{1,100}") String model) {
    }

    public record GoogleCalendarForm(
            @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean enabled,
            @Schema(example = "team@group.calendar.google.com") @Size(max = 255) String calendarId,
            @Schema(description = "Write-only: the service account key file's JSON; blank/omitted keeps the stored one")
            @Size(max = 20_000) String serviceAccountJson) {
    }

    // ------------------------------------------------------------------ views (no secret ever)

    /** configured = a row exists. */
    public record SlackView(boolean configured, boolean enabled, boolean hasWebhookUrl) {
    }

    public record TelegramView(boolean configured, boolean enabled, String chatId, boolean hasBotToken) {
    }

    public record TwilioView(boolean configured, boolean enabled, String accountSid, String fromNumber,
                             boolean hasAuthToken) {
    }

    public record AiView(boolean configured, boolean enabled, EAiProvider provider, String model, boolean hasApiKey) {
    }

    /** serviceAccountEmail: client_email of the key, so the admin can share the calendar with it. */
    public record GoogleCalendarView(boolean configured, boolean enabled, String calendarId,
                                     String serviceAccountEmail, boolean hasServiceAccountJson) {
    }

    public record IntegrationsView(SlackView slack, TelegramView telegram, TwilioView twilio, AiView aiAssistant,
                                   GoogleCalendarView googleCalendar) {
    }

    /** Only the status: the body of the answer is never read or returned. */
    public record TestResult(boolean delivered, Integer statusCode, String failure) {
        static TestResult of(OutboundHttp.Result r) {
            return new TestResult(r.delivered(), r.statusCode(), r.failure());
        }
    }
}
