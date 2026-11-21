package com.terp.erp.iam.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StripeTest {
    private static final String SECRET = "whsec_test_fake";
    private static final String PAYLOAD = "{\"type\":\"checkout.session.completed\"}";

    @Test
    void signatureMustMatchAndBeFresh() {
        Instant now = Instant.parse("2026-11-21T10:00:00Z");
        long t = now.getEpochSecond();
        String good = "t=" + t + ",v1=" + StripeSignature.sign(t + "." + PAYLOAD, SECRET);
        assertThat(StripeSignature.valid(PAYLOAD, good, SECRET, now)).isTrue();
        // several v1 values (secret rotation): any match is enough
        assertThat(StripeSignature.valid(PAYLOAD, "t=" + t + ",v1=deadbeef," + good.substring(good.indexOf("v1")), SECRET, now)).isTrue();
        assertThat(StripeSignature.valid(PAYLOAD + " ", good, SECRET, now)).isFalse();
        assertThat(StripeSignature.valid(PAYLOAD, good, "whsec_other", now)).isFalse();
        assertThat(StripeSignature.valid(PAYLOAD, good, SECRET, now.plusSeconds(301))).isFalse();
        assertThat(StripeSignature.valid(PAYLOAD, "v1=abc", SECRET, now)).isFalse();
        assertThat(StripeSignature.valid(PAYLOAD, null, SECRET, now)).isFalse();
    }

    @Test
    void amountsInStripesSmallestUnit() {
        assertThat(StripeClient.minorUnits(new BigDecimal("490000"), "VND")).isEqualTo(490000);
        assertThat(StripeClient.minorUnits(new BigDecimal("19.99"), "usd")).isEqualTo(1999);
        assertThat(StripeClient.minorUnits(new BigDecimal("1000.4"), "JPY")).isEqualTo(1000);
    }
}
