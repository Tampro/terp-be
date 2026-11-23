package com.terp.erp.iam.constant;

import java.util.Set;

/**
 * AI assistant (ERP manual 19, seeded in 20261123700000_ai_assistant_permission_init_data). Module AI is governed by
 * the plan module AI_ASSISTANT; the compose endpoint also requires the plan flag aiIntegration.
 */
public final class AiPermissions {
    public static final String USE = "AI_ASSISTANT_USE";
    public static final Set<String> ALL = Set.of(USE);

    private AiPermissions() {
    }
}
