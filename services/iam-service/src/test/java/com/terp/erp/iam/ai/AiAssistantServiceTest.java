package com.terp.erp.iam.ai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.ai.AiDtos.ComposeForm;
import com.terp.erp.iam.ai.AiDtos.ComposeResult;
import com.terp.erp.iam.ai.AiDtos.EAiLanguage;
import com.terp.erp.iam.ai.AiDtos.EAiPurpose;
import com.terp.erp.iam.ai.AiDtos.EAiTone;
import com.terp.erp.iam.ai.AiDtos.EAiUsageStatus;
import com.terp.erp.iam.integration.CompanyIntegrationService;
import com.terp.erp.iam.integration.CompanyIntegrationService.AiTarget;
import com.terp.erp.iam.integration.IntegrationDtos.EAiProvider;
import com.terp.erp.iam.integration.UrlGuard;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.saas.ESubscriptionStatus;
import com.terp.erp.iam.saas.SubscriptionService;
import com.terp.erp.iam.user.UserProfileDeleteTest;
import com.terp.erp.security.ErpPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAssistantServiceTest {
    private static final String KEY = AiProviderClientTest.KEY;
    private static final String CONTEXT = "Backend developer for [fake company], Java 17, Hanoi";
    private static final String OUTPUT = "GENERATED-OUTPUT-TEXT";
    private static final String OK_JSON = "{\"content\":[{\"type\":\"text\",\"text\":\"" + OUTPUT + "\"}]}";

    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
    private final CompanyIntegrationService integrations = mock(CompanyIntegrationService.class);
    private final AiHttp http = mock(AiHttp.class);
    private final AiUsageRepository usage = mock(AiUsageRepository.class);
    private final AiProperties props = new AiProperties(2, 800);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T10:00:30Z"), ZoneOffset.UTC);
    private final UrlGuard guard = new UrlGuard(host -> new InetAddress[]{InetAddress.getByName("54.230.10.10")});
    private final AiProviderClient client = new AiProviderClient(http, guard, new ObjectMapper());
    private final AiAssistantService service = new AiAssistantService(companies, subscriptions, integrations, client,
            new AiRateLimiter(props, clock), usage, props);
    private final Plan plan = new Plan();
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        UserProfileDeleteTest.login(7L, 10L, "AI_ASSISTANT_USE");
        Company c = new Company();
        c.setId(10L);
        plan.setAiIntegration(true);
        when(companies.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(c));
        when(subscriptions.effective(c)).thenAnswer(i ->
                new SubscriptionService.Effective(ESubscriptionStatus.ACTIVE, plan, null, false));
        when(integrations.aiTarget(10L)).thenReturn(Optional.of(new AiTarget(EAiProvider.ANTHROPIC, KEY,
                "claude-sonnet-4-5")));
        when(http.postJson(any(), anyString(), anyMap())).thenReturn(new AiHttp.Response(200, OK_JSON));
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(AiAssistantService.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(AiAssistantService.class)).detachAppender(logs);
        SecurityContextHolder.clearContext();
    }

    private static ComposeForm form(String context, String instructions) {
        return new ComposeForm(EAiPurpose.JOB_DESCRIPTION, EAiLanguage.VI, EAiTone.FORMAL, context, instructions);
    }

    @Test
    void composeSendsSystemPromptAndUserTextAndRecordsSizesOnly() {
        ComposeResult r = service.compose(form(CONTEXT, "Mention hybrid work"));

        assertThat(r).isEqualTo(new ComposeResult(OUTPUT, EAiProvider.ANTHROPIC, "claude-sonnet-4-5"));
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(http).postJson(any(), json.capture(), anyMap());
        assertThat(json.getValue()).contains("job description").contains(CONTEXT).contains("Vietnamese")
                .contains("formal and polite").contains("Mention hybrid work").contains("\"max_tokens\":800");

        ArgumentCaptor<AiUsage> row = ArgumentCaptor.forClass(AiUsage.class);
        verify(usage).save(row.capture());
        AiUsage u = row.getValue();
        assertThat(u.getCompanyId()).isEqualTo(10L);
        assertThat(u.getUserId()).isEqualTo(7L);
        assertThat(u.getPurpose()).isEqualTo(EAiPurpose.JOB_DESCRIPTION);
        assertThat(u.getStatus()).isEqualTo(EAiUsageStatus.OK);
        assertThat(u.getInputChars()).isEqualTo(CONTEXT.length() + "Mention hybrid work".length());
        assertThat(u.getOutputChars()).isEqualTo(OUTPUT.length());
        assertNoTextIn(Arrays.stream(AiUsage.class.getDeclaredFields()).map(f -> {
            f.setAccessible(true);
            try {
                return String.valueOf(f.get(u));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }).toList().toString());
        assertNoTextIn(logText());
        assertThat(logText()).contains("company=10").contains("purpose=JOB_DESCRIPTION").contains("status=OK");
    }

    @Test
    void superAdminIsRefusedWithoutCall() {
        ErpPrincipal p = new ErpPrincipal(null, "kc-sa", "sa", null, true, Set.of("SUPER_ADMIN"), Set.of());
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "kc-sa"));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), "kc-sa");
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertRefused(EAiError.AI_001, HttpStatus.FORBIDDEN);
    }

    @Test
    void missingPermissionIsRefusedWithoutCall() {
        UserProfileDeleteTest.login(7L, 10L, "IAM_USER_VIEW");
        assertThatThrownBy(() -> service.compose(form(CONTEXT, null))).isInstanceOf(AccessDeniedException.class);
        assertNoCall();
    }

    @Test
    void planWithoutAiIsRefusedWithoutCall() {
        plan.setAiIntegration(false);
        assertRefused(EAiError.AI_002, HttpStatus.FORBIDDEN);
    }

    @Test
    void noPlanIsRefusedWithoutCall() {
        when(subscriptions.effective(any())).thenReturn(
                new SubscriptionService.Effective(ESubscriptionStatus.NONE, null, null, false));
        assertRefused(EAiError.AI_002, HttpStatus.FORBIDDEN);
    }

    @Test
    void integrationOffOrMissingIsRefusedWithoutCall() {
        when(integrations.aiTarget(10L)).thenReturn(Optional.empty());
        assertRefused(EAiError.AI_003, HttpStatus.BAD_REQUEST);
    }

    @Test
    void tooLongInputIsRefusedWithoutCall() {
        assertRefused(form("x".repeat(AiDtos.MAX_CONTEXT + 1), null), EAiError.AI_004, HttpStatus.BAD_REQUEST);
        assertRefused(form(CONTEXT, "y".repeat(AiDtos.MAX_INSTRUCTIONS + 1)), EAiError.AI_004,
                HttpStatus.BAD_REQUEST);
        assertRefused(form("  ", null), EAiError.AI_004, HttpStatus.BAD_REQUEST);
    }

    @Test
    void rateLimitPerCompanyAnswers429WithoutCall() {
        service.compose(form(CONTEXT, null));
        service.compose(form(CONTEXT, null));
        assertThatThrownBy(() -> service.compose(form(CONTEXT, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(EAiError.AI_006);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                });
        verify(http, times(2)).postJson(any(), anyString(), anyMap());
        ArgumentCaptor<AiUsage> rows = ArgumentCaptor.forClass(AiUsage.class);
        verify(usage, times(3)).save(rows.capture());
        assertThat(rows.getAllValues().get(2).getStatus()).isEqualTo(EAiUsageStatus.RATE_LIMITED);
    }

    @Test
    void providerFailureIsRecordedAndLoggedWithoutTextOrKey() {
        when(http.postJson(any(), anyString(), anyMap())).thenReturn(new AiHttp.Response(401,
                "{\"error\":{\"type\":\"authentication_error\",\"message\":\"bad key " + KEY + "\"}}"));
        assertThatThrownBy(() -> service.compose(form(CONTEXT, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(EAiError.AI_005));
        ArgumentCaptor<AiUsage> row = ArgumentCaptor.forClass(AiUsage.class);
        verify(usage).save(row.capture());
        assertThat(row.getValue().getStatus()).isEqualTo(EAiUsageStatus.AUTH_FAILED);
        assertThat(row.getValue().getOutputChars()).isZero();
        assertNoTextIn(logText());
        assertThat(logText()).contains("status=AUTH_FAILED").contains("code=AI_005");
    }

    @Test
    void usageRowFailureDoesNotHideTheAnswer() {
        when(usage.save(any())).thenThrow(new RuntimeException("db down"));
        assertThat(service.compose(form(CONTEXT, null)).text()).isEqualTo(OUTPUT);
    }

    @Test
    void endpointsCarryTheirPermissions() throws NoSuchMethodException {
        assertThat(AiAssistantController.class.getMethod("compose", ComposeForm.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("@PermissionChecker.has('AI_ASSISTANT_USE')");
        assertThat(AiAssistantController.class.getMethod("usage", Long.class,
                org.springframework.data.domain.Pageable.class).getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@PermissionChecker.has('IAM_SETTINGS_MANAGE')");
    }

    @Test
    void everyPurposeHasASystemPromptWithTheCommonRules() {
        for (EAiPurpose p : EAiPurpose.values()) {
            assertThat(AiPrompts.SYSTEM.get(p)).as(p.name()).isNotBlank().endsWith(AiPrompts.COMMON);
        }
    }

    private void assertRefused(EAiError code, HttpStatus status) {
        assertRefused(form(CONTEXT, null), code, status);
    }

    private void assertRefused(ComposeForm f, EAiError code, HttpStatus status) {
        assertThatThrownBy(() -> service.compose(f)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(code);
            assertThat(e.getStatus()).isEqualTo(status);
        });
        assertNoCall();
    }

    private void assertNoCall() {
        verify(http, never()).postJson(any(), anyString(), anyMap());
        verify(usage, never()).save(any());
    }

    private String logText() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList().toString();
    }

    private static void assertNoTextIn(String s) {
        assertThat(s).doesNotContain(KEY).doesNotContain(CONTEXT).doesNotContain(OUTPUT)
                .doesNotContain("hybrid").doesNotContain("job description");
    }
}
