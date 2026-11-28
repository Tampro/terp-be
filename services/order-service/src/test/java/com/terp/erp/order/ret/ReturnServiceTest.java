package com.terp.erp.order.ret;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.order.TestLogin;
import com.terp.erp.order.client.CartSourcesClient;
import com.terp.erp.order.client.CartSourcesClient.Membership;
import com.terp.erp.order.common.EOrderError;
import com.terp.erp.order.common.EReturnError;
import com.terp.erp.order.mileage.MileageLedger;
import com.terp.erp.order.order.CustomerOrder;
import com.terp.erp.order.order.CustomerOrderLineRepository;
import com.terp.erp.order.order.CustomerOrderRepository;
import com.terp.erp.order.ret.ReturnDtos.LineForm;
import com.terp.erp.order.ret.ReturnDtos.RequestForm;
import com.terp.erp.outbox.OutboxPublisher;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReturnServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    private final OrderReturnRepository returns = mock(OrderReturnRepository.class);
    private final OrderReturnLineRepository returnLines = mock(OrderReturnLineRepository.class);
    private final ReturnPolicyRepository policies = mock(ReturnPolicyRepository.class);
    private final ReturnNumbers numbers = mock(ReturnNumbers.class);
    private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    private final CustomerOrderLineRepository orderLines = mock(CustomerOrderLineRepository.class);
    private final CartSourcesClient sources = mock(CartSourcesClient.class);
    private final MileageLedger mileage = mock(MileageLedger.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final ReturnService service = new ReturnService(returns, returnLines, policies, numbers, orders, orderLines,
            sources, mileage, outbox, tx, Clock.fixed(NOW, ZoneOffset.UTC));
    private final CustomerOrder order = ReturnRulesTest.order();
    private final List<OrderReturn> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        order.setCompanyId(10L);
        order.setCustomerId(5L);
        order.setStoreId(7L);
        order.setWarehouseId(3L);
        order.setOrderNo("HE-20261101-000001");
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(sources.membership("express", "sub-1")).thenReturn(new Membership(5L, 10L));
        when(orders.findByCompanyIdAndOrderNoAndCustomerId(10L, order.getOrderNo(), 5L)).thenReturn(Optional.of(order));
        when(orders.lockById(100L)).thenReturn(Optional.of(order));
        when(orders.findById(100L)).thenReturn(Optional.of(order));
        when(orderLines.findByOrderIdOrderByIdAsc(100L)).thenReturn(List.of(ReturnRulesTest.egg()));
        when(returnLines.takenByOrderLine(100L)).thenReturn(List.of());
        when(policies.findById(10L)).thenReturn(Optional.empty());
        when(numbers.next(eq(10L), any())).thenReturn("RQ-20261110-000001");
        when(mileage.earnedFor(100L)).thenReturn(1700);
        when(returns.saveAndFlush(any())).thenAnswer(a -> {
            OrderReturn r = a.getArgument(0);
            if (r.getId() == null) {
                r.setId(50L);
                saved.add(r);
            }
            return r;
        });
        when(returns.lockById(50L)).thenAnswer(a -> saved.stream().findFirst());
        when(returns.findById(50L)).thenAnswer(a -> saved.stream().findFirst());
        when(mileage.clawBack(any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(a -> a.getArgument(1));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private RequestForm form(EReturnReason reason, int qty) {
        return new RequestForm(reason, " Crushed ", List.of(new LineForm(1L, qty)));
    }

    @Test
    void aRequestPricesTheReturnUnderTheOrderLock() {
        ReturnDtos.ReturnDto d = service.request("Express", "sub-1", order.getOrderNo(), form(EReturnReason.CHANGE_OF_MIND, 2));
        assertThat(d.status()).isEqualTo(EReturnStatus.REQUESTED);
        assertThat(d.refundAmount()).isEqualByComparingTo("5500");
        assertThat(d.pickupFee()).isEqualByComparingTo("3000");
        assertThat(d.note()).isEqualTo("Crushed");
        verify(orders).lockById(100L);
    }

    @Test
    void requestRefusals() {
        OrderReturn open = new OrderReturn();
        open.setReturnNo("RQ-20261109-000003");
        when(returns.findFirstByOrderIdAndStatusIn(eq(100L), any())).thenReturn(Optional.of(open));
        code(() -> service.request("express", "sub-1", order.getOrderNo(), form(EReturnReason.DEFECT, 1)), EReturnError.RET_005);
        when(returns.findFirstByOrderIdAndStatusIn(eq(100L), any())).thenReturn(Optional.empty());
        when(returnLines.takenByOrderLine(100L)).thenReturn(List.<Object[]>of(new Object[]{7L, 3L}));
        code(() -> service.request("express", "sub-1", order.getOrderNo(), form(EReturnReason.DEFECT, 2)), EReturnError.RET_003);
        code(() -> service.request("express", "sub-1", "HE-OTHER", form(EReturnReason.DEFECT, 1)), EOrderError.ORD_018);
        order.setDeliveryStatus("ASSIGNED");
        code(() -> service.request("express", "sub-1", order.getOrderNo(), form(EReturnReason.DEFECT, 1)), EReturnError.RET_001);
        verify(returns, never()).saveAndFlush(any());
    }

    @Test
    void approveCanSwitchTheReasonAndRejectFreesTheLines() {
        service.request("express", "sub-1", order.getOrderNo(), form(EReturnReason.CHANGE_OF_MIND, 2));
        TestLogin.as(10L, "ORDER_RETURN_MANAGE");
        ReturnDtos.ReturnDto a = service.approve(50L, EReturnReason.DEFECT);
        assertThat(a.status()).isEqualTo(EReturnStatus.APPROVED);
        assertThat(a.refundAmount()).isEqualByComparingTo("8500");
        code(() -> service.reject(50L, "late"), EReturnError.RET_006);
        TestLogin.as(11L, "ORDER_RETURN_MANAGE");
        code(() -> service.approve(50L, null), EReturnError.RET_007);
    }

    @Test
    void completeMovesStockFirstThenPointsJournalAndEvent() {
        service.request("express", "sub-1", order.getOrderNo(), form(EReturnReason.CHANGE_OF_MIND, 2));
        TestLogin.as(10L, "ORDER_RETURN_MANAGE");
        service.approve(50L, null);
        when(returnLines.findByReturnIdOrderByIdAsc(50L)).thenReturn(List.of(line()));
        ReturnDtos.ReturnDto d = service.complete(50L);
        assertThat(d.status()).isEqualTo(EReturnStatus.COMPLETED);
        org.mockito.InOrder in = org.mockito.Mockito.inOrder(sources, mileage, outbox);
        ArgumentCaptor<List<CartSourcesClient.StockLine>> st = ArgumentCaptor.forClass(List.class);
        in.verify(sources).moveStock(eq(10L), eq(50L), st.capture(), eq("ORDER_RETURN"));
        in.verify(mileage).giveBack(order, 500);
        in.verify(mileage).clawBack(order, 850);
        assertThat(st.getValue().get(0).quantity()).isEqualByComparingTo("2");
        ArgumentCaptor<Map<String, Object>> j = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(eq(10L), eq("commerce.journal.requested"), eq("SHOP_ORDER_RETURN"), eq(50L), j.capture());
        assertThat((BigDecimal) j.getValue().get("goodsAmount")).isEqualByComparingTo("7727");
        assertThat((BigDecimal) j.getValue().get("serviceAmount")).isEqualByComparingTo("2727");
        assertThat((BigDecimal) j.getValue().get("taxAmount")).isEqualByComparingTo("500");
        ArgumentCaptor<Map<String, Object>> e = ArgumentCaptor.forClass(Map.class);
        verify(outbox).publish(eq(10L), eq("order.returned"), eq("ORDER"), eq(100L), e.capture());
        assertThat(e.getValue().keySet()).containsExactlyInAnyOrder("orderId", "returnId", "refundAmount");
        code(() -> service.complete(50L), EReturnError.RET_006);
    }

    @Test
    void aStockFailureLeavesTheReturnApproved() {
        service.request("express", "sub-1", order.getOrderNo(), form(EReturnReason.DEFECT, 1));
        TestLogin.as(10L, "ORDER_RETURN_MANAGE");
        service.approve(50L, null);
        when(returnLines.findByReturnIdOrderByIdAsc(50L)).thenReturn(List.of(line()));
        when(sources.moveStock(anyLong(), anyLong(), any(), eq("ORDER_RETURN")))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EOrderError.ORD_007));
        code(() -> service.complete(50L), EOrderError.ORD_007);
        assertThat(saved.get(0).getStatus()).isEqualTo(EReturnStatus.APPROVED);
        verify(outbox, never()).publish(any(), any(), any(), any(), any());
        verify(mileage, never()).giveBack(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private static OrderReturnLine line() {
        OrderReturnLine l = new OrderReturnLine();
        l.setItemId(1L);
        l.setName("Egg");
        l.setQuantity(2);
        l.setUnitPrice(new BigDecimal("5000"));
        l.setShare(new BigDecimal("8500"));
        l.setUnitCost(new BigDecimal("500"));
        return l;
    }

    private static void code(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(x -> assertThat(((ApiException) x).getErrorCode()).isEqualTo(expected));
    }
}
