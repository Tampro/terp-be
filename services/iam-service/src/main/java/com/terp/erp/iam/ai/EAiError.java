package com.terp.erp.iam.ai;

import com.terp.erp.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Never renumber or reuse a code: the FE keys on it. Provider answers are reduced to a status and an error code. */
@Getter
@RequiredArgsConstructor
public enum EAiError implements ErrorCode {
    AI_001("The AI assistant is only available to company users."),
    AI_002("The plan of your company does not include the AI assistant."),
    AI_003("The AI assistant integration is not configured or is turned off."),
    AI_004("The {0} is too long: at most {1} characters."),
    AI_005("The AI provider rejected the API key ({0}). Check the AI assistant integration."),
    AI_006("Too many AI requests. Try again in a minute."),
    AI_007("The AI provider is not available right now ({0})."),
    AI_008("The AI provider returned an answer that could not be read."),
    AI_009("The AI provider refused the request ({0})."),
    AI_010("The AI model name is not valid.");

    private final String message;
}
