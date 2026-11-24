package com.terp.erp.iam.saas;

import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.repository.CompanyRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanExpirySeatJobTest {
    @Test
    void enforcesEveryExpiredCompanyEvenWhenOneFails() {
        Instant now = Instant.parse("2026-11-24T05:00:00Z");
        CompanyRepository companies = mock(CompanyRepository.class);
        PlanSeatEnforcer seats = mock(PlanSeatEnforcer.class);
        Company a = new Company();
        a.setId(1L);
        Company b = new Company();
        b.setId(2L);
        when(companies.findByDeletedFalseAndPlanExpiresAtLessThanEqual(now)).thenReturn(List.of(a, b));
        when(seats.enforce(a)).thenThrow(new IllegalStateException("db"));

        new PlanExpirySeatJob(companies, seats, Clock.fixed(now, ZoneOffset.UTC)).run();

        verify(seats).enforce(b);
    }
}
