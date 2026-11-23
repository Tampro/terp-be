package com.terp.erp.iam.ai;

import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.ai.AiDtos.ComposeForm;
import com.terp.erp.iam.ai.AiDtos.ComposeResult;
import com.terp.erp.iam.ai.AiDtos.EAiUsageStatus;
import com.terp.erp.iam.ai.AiDtos.UsageView;
import com.terp.erp.iam.ai.AiPrompts.Prompt;
import com.terp.erp.iam.constant.AiPermissions;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.integration.CompanyIntegrationService;
import com.terp.erp.iam.integration.CompanyIntegrationService.AiTarget;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.saas.SubscriptionService;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI assistant (ERP manual 19). IAM calls the provider itself so the decrypted key never leaves this service.
 * The user's text reaches the provider only when every check passes, in this order and with no network before the
 * last one: company user, permission, plan aiIntegration, integration enabled, input sizes, model name, rate limit.
 * Never logs the context, instructions, prompt, output or key: only company, purpose, provider, status and latency.
 * Not @Transactional: no DB transaction is held during the up-to-30 s provider call; the usage row saves on its own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {
    private final CompanyRepository companies;
    private final SubscriptionService subscriptions;
    private final CompanyIntegrationService integrations;
    private final AiProviderClient client;
    private final AiRateLimiter limiter;
    private final AiUsageRepository usage;
    private final AiProperties props;

    public ComposeResult compose(ComposeForm f) {
        ErpPrincipal p = CurrentUser.get();
        if (p.superAdmin() || p.companyId() == null) {
            throw ApiException.forbidden(EAiError.AI_001);
        }
        if (!p.permissions().contains(AiPermissions.USE)) {
            // @PreAuthorize already refuses; kept so the service is never callable without it.
            throw new AccessDeniedException("AI_ASSISTANT_USE");
        }
        Long cid = p.companyId();
        Plan plan = companies.findByIdAndDeletedFalse(cid).map(c -> subscriptions.effective(c).plan()).orElse(null);
        if (plan == null || !plan.isAiIntegration()) {
            throw ApiException.forbidden(EAiError.AI_002);
        }
        AiTarget target = integrations.aiTarget(cid).orElseThrow(() -> ApiException.badRequest(EAiError.AI_003));
        checkSizes(f);
        if (!AiProviderClient.validModel(target.model())) {
            throw ApiException.badRequest(EAiError.AI_010);
        }
        int inputChars = f.context().length() + (f.instructions() == null ? 0 : f.instructions().length());
        if (!limiter.tryAcquire(cid)) {
            record(p, f, target, EAiUsageStatus.RATE_LIMITED, inputChars, 0, null);
            log.info("AI compose company={} purpose={} provider={} status={}", cid, f.purpose(), target.provider(),
                    EAiUsageStatus.RATE_LIMITED);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, EAiError.AI_006);
        }
        Prompt prompt = AiPrompts.build(f, props.maxOutputTokens());
        long start = System.nanoTime();
        try {
            String text = client.complete(target, prompt);
            long ms = (System.nanoTime() - start) / 1_000_000L;
            record(p, f, target, EAiUsageStatus.OK, inputChars, text.length(), ms);
            log.info("AI compose company={} purpose={} provider={} status={} latencyMs={}", cid, f.purpose(),
                    target.provider(), EAiUsageStatus.OK, ms);
            return new ComposeResult(text, target.provider(), target.model());
        } catch (ApiException e) {
            long ms = (System.nanoTime() - start) / 1_000_000L;
            EAiUsageStatus status = statusOf(e);
            record(p, f, target, status, inputChars, 0, ms);
            log.warn("AI compose company={} purpose={} provider={} status={} code={} latencyMs={}", cid, f.purpose(),
                    target.provider(), status, e.getErrorCode().getCode(), ms);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<UsageView> usage(Long requestedCompanyId, Pageable pageable) {
        Long cid = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        return usage.findByCompanyId(cid, pageable).map(u -> new UsageView(String.valueOf(u.getId()),
                u.getUserId() == null ? null : String.valueOf(u.getUserId()), u.getPurpose(), u.getProvider(),
                u.getModel(), u.getStatus(), u.getInputChars(), u.getOutputChars(), u.getLatencyMs(),
                u.getCreatedDate()));
    }

    /** Bean validation checks the same sizes at the controller; repeated so no caller can skip them. */
    private static void checkSizes(ComposeForm f) {
        if (f.purpose() == null || f.language() == null || f.context() == null || f.context().isBlank()) {
            throw ApiException.badRequest(EAiError.AI_004, "context", AiDtos.MAX_CONTEXT);
        }
        if (f.context().length() > AiDtos.MAX_CONTEXT) {
            throw ApiException.badRequest(EAiError.AI_004, "context", AiDtos.MAX_CONTEXT);
        }
        if (f.instructions() != null && f.instructions().length() > AiDtos.MAX_INSTRUCTIONS) {
            throw ApiException.badRequest(EAiError.AI_004, "instructions", AiDtos.MAX_INSTRUCTIONS);
        }
    }

    private static EAiUsageStatus statusOf(ApiException e) {
        if (e.getErrorCode() == EAiError.AI_005) {
            return EAiUsageStatus.AUTH_FAILED;
        }
        if (e.getErrorCode() == EAiError.AI_006) {
            return EAiUsageStatus.PROVIDER_RATE_LIMITED;
        }
        if (e.getErrorCode() == EAiError.AI_007) {
            return EAiUsageStatus.UNAVAILABLE;
        }
        if (e.getErrorCode() == EAiError.AI_008) {
            return EAiUsageStatus.BAD_RESPONSE;
        }
        return EAiUsageStatus.REFUSED;
    }

    private void record(ErpPrincipal p, ComposeForm f, AiTarget t, EAiUsageStatus status, int inputChars,
                        int outputChars, Long latencyMs) {
        AiUsage u = new AiUsage();
        u.setCompanyId(p.companyId());
        u.setUserId(p.userId());
        u.setPurpose(f.purpose());
        u.setProvider(t.provider());
        u.setModel(t.model());
        u.setStatus(status);
        u.setInputChars(inputChars);
        u.setOutputChars(outputChars);
        u.setLatencyMs(latencyMs);
        try {
            usage.save(u);
        } catch (RuntimeException e) {
            // The answer is already decided; a lost usage row must not turn it into an error.
            log.error("AI usage row not saved company={} status={}", p.companyId(), status);
        }
    }
}
