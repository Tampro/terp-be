package com.terp.erp.order.ret;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.order.common.EReturnError;
import com.terp.erp.order.order.CustomerOrder;
import com.terp.erp.order.order.CustomerOrderLine;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReturnRulesTest {
    static CustomerOrder order() {
        CustomerOrder o = new CustomerOrder();
        o.setId(100L);
        o.setSubtotal(new BigDecimal("20000"));
        o.setDiscount(new BigDecimal("2000"));
        o.setPointsUsed(1000);
        o.setDeliveryStatus("DELIVERED");
        o.setDeliveryStatusAt(Instant.parse("2026-11-01T14:00:00Z"));       // 2026-11-01 23:00 KST
        return o;
    }

    static CustomerOrderLine egg() {
        CustomerOrderLine l = new CustomerOrderLine();
        l.setId(7L);
        l.setItemId(1L);
        l.setName("Egg");
        l.setUnitPrice(new BigDecimal("5000"));
        l.setQuantity(4);
        l.setUnitCost(new BigDecimal("500"));
        return l;
    }

    @Test
    void theRefundIsWhatWasPaidForTheUnitsMinusThePickUpFee() {
        // goods 10,000 of 20,000; paid 17,000 for the order → share 8,500; fee 3,000 → 5,500
        ReturnRules.Quote q = ReturnRules.quote(order(), List.of(egg()), Map.of(), List.of(new ReturnRules.Requested(1L, 2)),
                EReturnReason.CHANGE_OF_MIND, new BigDecimal("3000"), 1700);
        assertThat(q.goods()).isEqualByComparingTo("10000");
        assertThat(q.share()).isEqualByComparingTo("8500");
        assertThat(q.refund()).isEqualByComparingTo("5500");
        assertThat(q.cost()).isEqualByComparingTo("1000");
        assertThat(q.pointsBack()).isEqualTo(500);
        assertThat(q.earnedBack()).isEqualTo(850);
        ReturnRules.Quote free = ReturnRules.quote(order(), List.of(egg()), Map.of(), List.of(new ReturnRules.Requested(1L, 2)),
                EReturnReason.DEFECT, new BigDecimal("3000"), 0);
        assertThat(free.refund()).isEqualByComparingTo("8500");
    }

    @Test
    void theJournalNetsTheFeeVatAndPaysBackTheRefund() {
        ReturnRules.JournalAmounts a = ReturnRules.journal(new BigDecimal("8500"), new BigDecimal("3000"), new BigDecimal("1000"));
        assertThat(a.goods()).isEqualByComparingTo("7727");
        assertThat(a.service()).isEqualByComparingTo("2727");
        assertThat(a.tax()).isEqualByComparingTo("500");
        assertThat(a.goods().add(a.tax()).subtract(a.service())).isEqualByComparingTo("5500");
    }

    @Test
    void refusals() {
        List<CustomerOrderLine> lines = List.of(egg());
        code(() -> ReturnRules.quote(order(), lines, Map.of(7L, 3), List.of(new ReturnRules.Requested(1L, 2)),
                EReturnReason.DEFECT, BigDecimal.ZERO, 0), EReturnError.RET_003);
        code(() -> ReturnRules.quote(order(), lines, Map.of(), List.of(new ReturnRules.Requested(9L, 1)),
                EReturnReason.DEFECT, BigDecimal.ZERO, 0), EReturnError.RET_003);
        code(() -> ReturnRules.quote(order(), lines, Map.of(), List.of(new ReturnRules.Requested(1L, 1), new ReturnRules.Requested(1L, 4)),
                EReturnReason.DEFECT, BigDecimal.ZERO, 0), EReturnError.RET_003);
        code(() -> ReturnRules.quote(order(), lines, Map.of(), List.of(new ReturnRules.Requested(1L, 1)),
                EReturnReason.CHANGE_OF_MIND, new BigDecimal("5000"), 0), EReturnError.RET_004);   // share 4,250 < 5,000
    }

    @Test
    void theWindowIsThirtySeoulDaysFromDelivery() {
        CustomerOrder o = order();
        ReturnRules.checkWindow(o, Instant.parse("2026-12-01T14:59:59Z"));                    // 2026-12-01 23:59 KST
        code(() -> ReturnRules.checkWindow(o, Instant.parse("2026-12-01T15:00:00Z")), EReturnError.RET_002);
        o.setDeliveryStatus("PICKED_UP");
        code(() -> ReturnRules.checkWindow(o, Instant.parse("2026-11-02T00:00:00Z")), EReturnError.RET_001);
    }

    private static void code(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(x -> assertThat(((ApiException) x).getErrorCode()).isEqualTo(expected));
    }
}
