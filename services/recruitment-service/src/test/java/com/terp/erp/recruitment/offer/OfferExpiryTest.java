package com.terp.erp.recruitment.offer;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferExpiryTest {
    @Test
    void expiresOpenOffersByTheVietnamDate() {
        OfferRepository offers = mock(OfferRepository.class);
        // 18:00 UTC on the 21st is already the 22nd in Vietnam
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T18:00:00Z"), ZoneOffset.UTC);
        OfferService service = new OfferService(offers, null, null, null, null, null, null, null, clock);
        when(offers.expireOverdue(any(), any(), any())).thenReturn(3);

        assertThat(service.expireOverdue()).isEqualTo(3);
        verify(offers).expireOverdue(eq(LocalDate.of(2026, 11, 22)),
                eq(EnumSet.of(Offer.EStatus.DRAFT, Offer.EStatus.SENT, Offer.EStatus.NEGOTIATING)),
                eq(Instant.parse("2026-11-21T18:00:00Z")));
    }

    @Test
    void aFailingRunIsLoggedNotThrown() {
        OfferService service = mock(OfferService.class);
        when(service.expireOverdue()).thenThrow(new IllegalStateException("db down"));
        new OfferExpiryJob(service).run();
    }
}
