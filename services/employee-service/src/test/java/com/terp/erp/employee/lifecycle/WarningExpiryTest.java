package com.terp.erp.employee.lifecycle;

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

class WarningExpiryTest {
    @Test
    void expiresWarningsInForceByTheVietnamDate() {
        EmployeeWarningRepository warnings = mock(EmployeeWarningRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T18:00:00Z"), ZoneOffset.UTC);
        WarningService service = new WarningService(warnings, null, null, null, clock);
        when(warnings.expireOverdue(any(), any(), any())).thenReturn(2);

        assertThat(service.expireOverdue()).isEqualTo(2);
        verify(warnings).expireOverdue(eq(LocalDate.of(2026, 11, 22)),
                eq(EnumSet.of(EmployeeWarning.EStatus.ISSUED, EmployeeWarning.EStatus.ACKNOWLEDGED)),
                eq(Instant.parse("2026-11-21T18:00:00Z")));
    }
}
