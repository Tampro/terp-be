package com.terp.erp.iam.ai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.terp.erp.iam.integration.IntegrationDtos.EAiProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Locale;

public final class AiDtos {
    public static final int MAX_CONTEXT = 4000;
    public static final int MAX_INSTRUCTIONS = 500;

    private AiDtos() {
    }

    /**
     * What to draft. The reference (TERP Core AIAssistant, AIPromptSeeder) has one prompt per form field; these are
     * the texts users write at length in this ERP: job postings, support replies, emails, announcements.
     */
    public enum EAiPurpose { JOB_DESCRIPTION, TICKET_REPLY, EMAIL, ANNOUNCEMENT, FREE }

    public enum EAiTone { FORMAL, FRIENDLY, SHORT }

    /** Output language; JSON value is the lowercase code (vi, en, ko). */
    public enum EAiLanguage {
        VI("Vietnamese"), EN("English"), KO("Korean");

        public final String displayName;

        EAiLanguage(String displayName) {
            this.displayName = displayName;
        }

        @JsonValue
        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }

        @JsonCreator
        public static EAiLanguage of(String code) {
            return code == null ? null : valueOf(code.trim().toUpperCase(Locale.ROOT));
        }
    }

    /** Outcome of a compose attempt that passed the pre-checks (ai_usage.status). */
    public enum EAiUsageStatus { OK, RATE_LIMITED, AUTH_FAILED, PROVIDER_RATE_LIMITED, UNAVAILABLE, BAD_RESPONSE,
        REFUSED }

    @Schema(description = "A drafting request. The context and instructions are sent to the company's AI provider.")
    public record ComposeForm(
            @Schema(description = "What to draft", example = "JOB_DESCRIPTION", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EAiPurpose purpose,
            @Schema(description = "Output language: vi, en or ko", example = "vi",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull EAiLanguage language,
            @Schema(description = "Optional tone", example = "FORMAL") EAiTone tone,
            @Schema(description = "What the user typed or selected (at most 4000 characters)",
                    example = "Backend developer, Java 17, Spring Boot, 3 years, Hanoi office",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = MAX_CONTEXT) String context,
            @Schema(description = "Optional extra instructions (at most 500 characters)",
                    example = "Mention hybrid work") @Size(max = MAX_INSTRUCTIONS) String instructions) {
    }

    public record ComposeResult(String text, EAiProvider provider, String model) {
    }

    /** One usage row: sizes only, never the text. */
    public record UsageView(String id, String userId, EAiPurpose purpose, EAiProvider provider, String model,
                            EAiUsageStatus status, int inputChars, int outputChars, Long latencyMs,
                            Instant createdAt) {
    }
}
