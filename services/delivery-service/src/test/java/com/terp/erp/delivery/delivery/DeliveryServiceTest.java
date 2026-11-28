package com.terp.erp.delivery.delivery;

import com.terp.erp.delivery.TestLogin;
import com.terp.erp.delivery.client.IamClient;
import com.terp.erp.delivery.client.OrderClient;
import com.terp.erp.delivery.common.EDeliveryError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-28T03:00:00Z");
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final OrderClient orders = mock(OrderClient.class);
    private final IamClient iam = mock(IamClient.class);
    private final DeliveryService service = new DeliveryService(deliveries, orders, iam, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Delivery d = new Delivery();

    @BeforeEach
    void setUp() {
        d.setId(1L);
        d.setCompanyId(10L);
        d.setOrderId(100L);
        d.setOrderNo("HE-20261128-000001");
        d.setStoreId(7L);
        d.setStatus(EDeliveryStatus.READY);
        when(deliveries.findById(1L)).thenReturn(Optional.of(d));
        when(deliveries.saveAndFlush(any())).thenAnswer(a -> a.getArgument(0));
        when(deliveries.findByIdAndCompanyIdAndRiderSub(1L, 10L, "sub-r")).thenAnswer(a ->
                "sub-r".equals(d.getRiderSub()) ? Optional.of(d) : Optional.empty());
        when(iam.rider(10L, 42L)).thenReturn(new IamClient.Rider("42", "sub-r", "Rider One", true, true));
        when(iam.rider(10L, 43L)).thenReturn(new IamClient.Rider("43", "sub-x", "No Ride", true, false));
        when(orders.order(10L, 100L)).thenReturn(new OrderClient.OrderView("HE-20261128-000001", "PAID", "Store",
                "Hong", "01012345678", "08502", "1 Test-ro", null, null, null, null, null, new BigDecimal("13000"),
                List.of(new OrderClient.Line("Egg", 2))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void backOfficeAssignsThenTheRiderPicksUpAndDelivers() {
        TestLogin.as(10L, "DELIVERY_MANAGE");
        assertThat(service.get(1L).destination().phone()).isEqualTo("010-****-5678");
        DeliveryDtos.DeliveryDto a = service.assign(1L, 42L);
        assertThat(a.status()).isEqualTo(EDeliveryStatus.ASSIGNED);
        assertThat(a.riderSub()).isEqualTo("sub-r");
        assertThat(a.assignedAt()).isEqualTo(NOW);

        TestLogin.asUser("sub-r", 10L, "DELIVERY_RIDE");
        assertThat(service.myDelivery(1L).destination().phone()).isEqualTo("01012345678");
        assertCode(() -> service.deliver(1L), EDeliveryError.DLV_005);
        assertThat(service.pickUp(1L).status()).isEqualTo(EDeliveryStatus.PICKED_UP);
        assertThat(service.deliver(1L).status()).isEqualTo(EDeliveryStatus.DELIVERED);

        TestLogin.as(10L, "DELIVERY_MANAGE");
        assertCode(() -> service.cancel(1L, "late"), EDeliveryError.DLV_005);
        assertCode(() -> service.assign(1L, 42L), EDeliveryError.DLV_005);
    }

    @Test
    void refusals() {
        TestLogin.as(10L, "DELIVERY_MANAGE");
        assertCode(() -> service.assign(1L, 43L), EDeliveryError.DLV_004);           // no DELIVERY_RIDE
        TestLogin.as(11L, "DELIVERY_MANAGE");
        assertCode(() -> service.get(1L), EDeliveryError.DLV_001);                   // another company
        TestLogin.as(10L, "DELIVERY_MANAGE");
        service.assign(1L, 42L);
        TestLogin.asUser("sub-other", 10L, "DELIVERY_RIDE");
        assertCode(() -> service.pickUp(1L), EDeliveryError.DLV_001);                // another rider
        TestLogin.as(10L, "DELIVERY_MANAGE");
        assertThat(service.cancel(1L, " customer asked ").cancelReason()).isEqualTo("customer asked");
    }

    @Test
    void aConcurrentChangeIsAConflict() {
        TestLogin.as(10L, "DELIVERY_MANAGE");
        when(deliveries.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(Delivery.class, 1L));
        assertCode(() -> service.assign(1L, 42L), EDeliveryError.DLV_006);
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
