package com.terp.erp.iam.saas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.model.PlanRequest;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.PlanRequestRepository;
import com.terp.erp.security.ErpPrincipal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanRequestServiceTest {
    private PlanRequestRepository requests;
    private CatalogService catalog;
    private OrderService orders;
    private PlanRequestService service;
    private Plan pro;

    @BeforeEach
    void setUp() {
        requests = mock(PlanRequestRepository.class);
        catalog = mock(CatalogService.class);
        orders = mock(OrderService.class);
        service = new PlanRequestService(requests, mock(CompanyRepository.class), catalog, orders, Clock.systemUTC(),
                mock(com.terp.erp.iam.email.EmailNotifier.class));
        pro = new Plan();
        pro.setId(4L);
        pro.setName("Pro");
        pro.setMonthlyPrice(BigDecimal.TEN);
        pro.setEnabled(true);
        when(catalog.requirePlan(4L)).thenReturn(pro);
        when(catalog.findAny(4L)).thenReturn(pro);
        when(requests.save(any(PlanRequest.class))).thenAnswer(inv -> {
            PlanRequest r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requestIsCreatedForTheCallersCompanyOnly() {
        login(false, 10L);
        var dto = service.create(new SaasDtos.PlanRequestForm(4L, EBillingCycle.MONTHLY, "need more seats"));
        assertThat(dto.companyId()).isEqualTo("10");
        assertThat(dto.status()).isEqualTo(EPlanRequestStatus.PENDING);
    }

    @Test
    void secondPendingRequestIsRefused() {
        login(false, 10L);
        when(requests.existsByCompanyIdAndStatus(10L, EPlanRequestStatus.PENDING)).thenReturn(true);
        assertError(() -> service.create(new SaasDtos.PlanRequestForm(4L, EBillingCycle.MONTHLY, null)), ESaasError.SAAS_018);
    }

    @Test
    void yearlyRequestForAPlanWithoutYearlyPriceIsRefused() {
        login(false, 10L);
        assertError(() -> service.create(new SaasDtos.PlanRequestForm(4L, EBillingCycle.YEARLY, null)), ESaasError.SAAS_003);
    }

    @Test
    void approvalActivatesThePlanAndLinksTheManualOrder() {
        login(true, null);
        PlanRequest r = pending(10L);
        when(orders.activateManually(10L, 4L, EBillingCycle.MONTHLY, "Plan request #1"))
                .thenReturn(new SaasDtos.OrderDto("77", "ORD1", "10", "4", EBillingCycle.MONTHLY, null, BigDecimal.TEN,
                        BigDecimal.ZERO, BigDecimal.TEN, EPaymentMethod.MANUAL, null, EOrderStatus.APPROVED, null, null,
                        null, null, null));

        var dto = service.approve(1L, "ok");

        assertThat(dto.status()).isEqualTo(EPlanRequestStatus.APPROVED);
        assertThat(dto.orderId()).isEqualTo("77");
        assertThat(r.getProcessedBy()).isEqualTo("kc-root");
    }

    @Test
    void onlyPendingRequestsCanBeDecided() {
        login(true, null);
        pending(10L).setStatus(EPlanRequestStatus.REJECTED);
        assertError(() -> service.approve(1L, null), ESaasError.SAAS_017);
        verify(orders, never()).activateManually(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    void anotherCompanysRequestIsNotFound() {
        login(false, 11L);
        pending(10L);
        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getErrorCode()).isEqualTo(ESaasError.SAAS_016);
                    assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void statusCountsAddUp() {
        var counts = SaasDtos.StatusCountsDto.of(List.of(
                new Object[]{EPlanRequestStatus.PENDING, 2L}, new Object[]{EPlanRequestStatus.APPROVED, 3L}));
        assertThat(counts.all()).isEqualTo(5);
        assertThat(counts.rejected()).isZero();
    }

    private PlanRequest pending(Long companyId) {
        PlanRequest r = new PlanRequest();
        r.setId(1L);
        r.setCompanyId(companyId);
        r.setPlanId(4L);
        r.setBillingCycle(EBillingCycle.MONTHLY);
        r.setStatus(EPlanRequestStatus.PENDING);
        when(requests.findById(1L)).thenReturn(Optional.of(r));
        return r;
    }

    private static void login(boolean superAdmin, Long companyId) {
        ErpPrincipal p = superAdmin
                ? new ErpPrincipal(null, "kc-root", "root", null, true, Set.of(), Set.of())
                : new ErpPrincipal(5L, "kc-5", "u5", companyId, false, Set.of(), Set.of(SaasPermissions.ORDER_CREATE));
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", p.keycloakId()));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), p.keycloakId());
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static void assertError(Runnable call, ESaasError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
