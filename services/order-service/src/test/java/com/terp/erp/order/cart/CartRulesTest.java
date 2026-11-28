package com.terp.erp.order.cart;

import com.terp.erp.order.cart.CartRules.Priced;
import com.terp.erp.order.client.CartSourcesClient.StoreView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-ORD-002 with the Homeplus Express numbers: delivery 3,000 under 30,000, bag 200, first order 10,000 / else 20,000. */
class CartRulesTest {
    private static StoreView store(int firstOrderMin) {
        return new StoreView("7", "Express", 245L, bd(3000), bd(30000), bd(20000), bd(firstOrderMin), bd(200), true, null);
    }

    @Test
    void deliveryIsFreeFromTheThresholdInclusive() {
        var under = CartRules.summary(store(10000), List.of(new Priced(bd(29999), true)), false);
        assertThat(under.deliveryFee()).isEqualByComparingTo("3000");
        assertThat(under.total()).isEqualByComparingTo("32999");
        var at = CartRules.summary(store(10000), List.of(new Priced(bd(30000), true)), false);
        assertThat(at.deliveryFee()).isEqualByComparingTo("0");
    }

    @Test
    void bagOnlyWhenAsked() {
        assertThat(CartRules.summary(store(10000), List.of(new Priced(bd(5000), true)), true).bagFee()).isEqualByComparingTo("200");
        assertThat(CartRules.summary(store(10000), List.of(new Priced(bd(5000), true)), false).bagFee()).isEqualByComparingTo("0");
    }

    @Test
    void unavailableLinesAreLeftOutOfTheTotalsAndTheMinimum() {
        var s = CartRules.summary(store(10000), List.of(new Priced(bd(9000), true), new Priced(bd(50000), false)), false);
        assertThat(s.subtotal()).isEqualByComparingTo("9000");
        assertThat(s.minimumReached()).isFalse();
    }

    @Test
    void firstOrderMinimumWhenSetElseTheStoreMinimum() {
        assertThat(CartRules.summary(store(10000), List.of(new Priced(bd(10000), true)), false).minimumReached()).isTrue();
        var noFirst = CartRules.summary(store(0), List.of(new Priced(bd(19999), true)), false);
        assertThat(noFirst.minOrderAmount()).isEqualByComparingTo("20000");
        assertThat(noFirst.minimumReached()).isFalse();
    }

    private static BigDecimal bd(int v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void freeDeliveryAndTheMinimumCompareTheGoodsAfterTheDiscount() {
        var s = CartRules.summary(store(10000), List.of(new Priced(bd(32000), true)), false, bd(3000));
        assertThat(s.deliveryFee()).isEqualByComparingTo("3000");               // 29,000 < 30,000 free threshold
        assertThat(s.total()).isEqualByComparingTo("32000");                     // 29,000 + 3,000
        assertThat(CartRules.summary(store(10000), List.of(new Priced(bd(11000), true)), false, bd(2000)).minimumReached())
                .isFalse();                                                     // 9,000 < 10,000
    }
}
