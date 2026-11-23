package com.terp.erp.iam.ai;

import com.terp.erp.iam.ai.AiDtos.EAiPurpose;
import com.terp.erp.iam.ai.AiDtos.EAiUsageStatus;
import com.terp.erp.iam.integration.IntegrationDtos.EAiProvider;
import com.terp.erp.iam.model.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One AI compose attempt that passed the pre-checks. Sizes only: no prompt, context or output is stored. */
@Getter
@Setter
@Entity
@Table(name = "ai_usage")
public class AiUsage extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private EAiPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private EAiProvider provider;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EAiUsageStatus status;

    @Column(name = "input_chars", nullable = false)
    private int inputChars;

    @Column(name = "output_chars", nullable = false)
    private int outputChars;

    @Column(name = "latency_ms")
    private Long latencyMs;
}
