package com.terp.erp.iam.saas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.model.PlanOrder;
import com.terp.erp.iam.payment.PaypalClient;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.PlanOrderRepository;
import com.terp.erp.iam.settings.SettingsDtos;
import com.terp.erp.iam.settings.SettingsService;
import com.terp.erp.security.ErpPrincipal;

import java.lang.reflect.Constructor;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServicePaypalTest {
    static final SettingsService.ActivePaypal PAYPAL = new SettingsService.ActivePaypal(
            SettingsDtos.EPaypalMode.SANDBOX, "fake-client", "fake-secret");

    private PlanOrderRepository orders;
    private SettingsService settings;
    private PaypalClient paypal;
    private SubscriptionService subscriptions;
    private OrderService service;
    private PlanOrder order;

    @BeforeEach
    void setUp() throws Exception {
        orders = mock(PlanOrderRepository.class);
        settings = mock(SettingsService.class);
        paypal = mock(PaypalClient.class);
        subscriptions = mock(SubscriptionService.class);
        CatalogService catalog = mock(CatalogService.class);
        CompanyRepository companies = mock(CompanyRepository.class);
        Constructor<?> ctor = OrderService.class.getDeclaredConstructors()[0];
        Object[] args = new Object[ctor.getParameterCount()];
        Class<?>[] types = ctor.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (types[i] == PlanOrderRepository.class) args[i] = orders;
            else if (types[i] == SettingsService.class) args[i] = settings;
            else if (types[i] == PaypalClient.class) args[i] = paypal;
            else if (types[i] == SubscriptionService.class) args[i] = subscriptions;
            else if (types[i] == CatalogService.class) args[i] = catalog;
            else if (types[i] == CompanyRepository.class) args[i] = companies;
            else if (types[i] == Clock.class) args[i] = Clock.systemUTC();
            else args[i] = mock(types[i]);
        }
        service = (OrderService) ctor.newInstance(args);
        ReflectionTestUtils.setField(service, "appUrl", "https://app.example.test");

        Plan pro = new Plan();
        pro.setId(4L);
        pro.setName("Pro");
        when(catalog.requirePlan(4L)).thenReturn(pro);
        Company c = new Company();
        c.setId(10L);
        when(companies.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(c));
        when(settings.bankTransfer()).thenReturn(new SettingsDtos.BankTransferSettings(true, null, null, null, null));
        when(settings.currency()).thenReturn(new SettingsDtos.CurrencySettingsView(null, "USD", "$"));
        when(settings.activePaypal()).thenReturn(PAYPAL);

        order = new PlanOrder();
        order.setId(42L);
        order.setOrderNumber("ORD261123000001");
        order.setCompanyId(10L);
        order.setPlanId(4L);
        order.setBillingCycle(EBillingCycle.MONTHLY);
        order.setFinalPrice(new BigDecimal("19.90"));
        order.setPaymentMethod(EPaymentMethod.PAYPAL);
        order.setStatus(EOrderStatus.PENDING);
        when(orders.findById(42L)).thenReturn(Optional.of(order));
        when(orders.findLockedById(42L)).thenReturn(Optional.of(order));
        login(10L);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkoutCreatesAPaypalOrderWithTheOrdersAmountAndReturnUrls() {
        when(paypal.createOrder(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(new PaypalClient.Created("PP-1", "https://www.sandbox.paypal.com/x"));
        var dto = service.paypalCheckout(42L);
        assertThat(dto).isEqualTo(new SaasDtos.PaypalCheckoutDto("42", "PP-1", "https://www.sandbox.paypal.com/x"));
        assertThat(order.getPaypalOrderId()).isEqualTo("PP-1");
        verify(paypal).createOrder(eq(PAYPAL), eq("42"), eq("10:42"), anyString(), eq("USD"), eq("19.90"),
                eq("https://app.example.test/billing/orders/42?paypal=return"),
                eq("https://app.example.test/billing/orders/42?paypal=cancel"), eq("erp-order-42-create-19.90"));
    }

    @Test
    void disabledPaypalFailsBeforeAnyHttp() {
        when(settings.activePaypal()).thenReturn(null);
        assertError(() -> service.paypalCheckout(42L), ESaasError.SAAS_042);
        order.setPaypalOrderId("PP-1");
        assertError(() -> service.paypalCapture(42L), ESaasError.SAAS_042);
        verifyNoInteractions(paypal);
    }

    @Test
    void unsupportedCurrencyFailsBeforeAnyHttp() {
        when(settings.currency()).thenReturn(new SettingsDtos.CurrencySettingsView(null, "VND", "₫"));
        assertError(() -> service.paypalCheckout(42L), ESaasError.SAAS_043);
        verifyNoInteractions(paypal);
    }

    @Test
    void otherCompanysOrderIs404() {
        login(11L);
        assertThatThrownBy(() -> service.paypalCheckout(42L)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ESaasError.SAAS_008);
            assertThat(e.getStatus().value()).isEqualTo(404);
        });
        assertThatThrownBy(() -> service.paypalCapture(42L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus().value()).isEqualTo(404));
        verifyNoInteractions(paypal);
    }

    @Test
    void nonPaypalOrderCannotBePaidThisWay() {
        order.setPaymentMethod(EPaymentMethod.BANK_TRANSFER);
        assertError(() -> service.paypalCheckout(42L), ESaasError.SAAS_039);
    }

    @Test
    void completedCaptureActivatesOnceAndASecondCallReturnsTheOrder() {
        order.setPaypalOrderId("PP-1");
        when(paypal.capture(PAYPAL, "PP-1", "erp-order-42-capture"))
                .thenReturn(new PaypalClient.Captured("COMPLETED", "42", "10:42", "CAP-1", "COMPLETED", "USD", "19.90"));
        var dto = service.paypalCapture(42L);
        assertThat(dto.status()).isEqualTo(EOrderStatus.APPROVED);
        assertThat(order.getPaymentReference()).isEqualTo("CAP-1");
        assertThat(order.getProcessedBy()).isEqualTo("paypal");

        var again = service.paypalCapture(42L);
        assertThat(again.status()).isEqualTo(EOrderStatus.APPROVED);
        verify(paypal, times(1)).capture(any(), anyString(), anyString());
        verify(subscriptions, times(1)).activate(any(), any(), eq(EBillingCycle.MONTHLY));
    }

    @Test
    void mismatchedAmountCurrencyOrReferenceIsRefusedAndNotActivated() {
        order.setPaypalOrderId("PP-1");
        for (PaypalClient.Captured bad : List.of(
                new PaypalClient.Captured("COMPLETED", "42", "10:42", "CAP-1", "COMPLETED", "USD", "1.00"),
                new PaypalClient.Captured("COMPLETED", "42", "10:42", "CAP-1", "COMPLETED", "EUR", "19.90"),
                new PaypalClient.Captured("COMPLETED", "42", "11:42", "CAP-1", "COMPLETED", "USD", "19.90"),
                new PaypalClient.Captured("COMPLETED", "43", "10:42", "CAP-1", "COMPLETED", "USD", "19.90"))) {
            when(paypal.capture(any(), anyString(), anyString())).thenReturn(bad);
            assertError(() -> service.paypalCapture(42L), ESaasError.SAAS_048);
            assertThat(order.getStatus()).isEqualTo(EOrderStatus.PENDING);
        }
        verify(subscriptions, never()).activate(any(), any(), any());
    }

    @Test
    void captureNotCompletedIsNotApproved() {
        order.setPaypalOrderId("PP-1");
        when(paypal.capture(any(), anyString(), anyString()))
                .thenReturn(new PaypalClient.Captured("COMPLETED", "42", "10:42", "CAP-1", "PENDING", "USD", "19.90"));
        assertError(() -> service.paypalCapture(42L), ESaasError.SAAS_046);
        verify(subscriptions, never()).activate(any(), any(), any());
    }

    @Test
    void alreadyCapturedIsReadBackVerifiedAndActivated() {
        order.setPaypalOrderId("PP-1");
        when(paypal.capture(any(), anyString(), anyString())).thenThrow(new PaypalClient.AlreadyCapturedException());
        when(paypal.getOrder(PAYPAL, "PP-1"))
                .thenReturn(new PaypalClient.Captured("COMPLETED", "42", "10:42", "CAP-1", "COMPLETED", "USD", "19.90"));
        assertThat(service.paypalCapture(42L).status()).isEqualTo(EOrderStatus.APPROVED);
        verify(subscriptions, times(1)).activate(any(), any(), any());
    }

    @Test
    void endpointsCarryTheOrderCreatePermissionInACompany() throws NoSuchMethodException {
        for (String m : List.of("paypalCheckout", "paypalCapture")) {
            assertThat(SubscriptionController.class.getMethod(m, Long.class).getAnnotation(PreAuthorize.class).value())
                    .isEqualTo("@PermissionChecker.hasInCompany('SAAS_ORDER_CREATE')");
        }
        assertThat(com.terp.erp.iam.settings.SettingsController.class.getMethod("paypal")
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("@PermissionChecker.isSuperAdmin()");
        assertThat(com.terp.erp.iam.settings.SettingsController.class
                .getMethod("savePaypal", SettingsDtos.PaypalSettingsForm.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("@PermissionChecker.isSuperAdmin()");
    }

    private static void login(Long companyId) {
        ErpPrincipal p = new ErpPrincipal(5L, "kc-5", "u5", companyId, false, Set.of(),
                Set.of(SaasPermissions.ORDER_CREATE));
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", p.keycloakId()));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), p.keycloakId());
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static void assertError(Runnable call, ESaasError code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
