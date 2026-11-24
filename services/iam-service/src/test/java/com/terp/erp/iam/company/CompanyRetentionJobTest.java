package com.terp.erp.iam.company;

import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyRetentionJobTest {
    @Test
    void anonymisesCompaniesDeletedFiveYearsAgoAndSkipsAFailure() {
        Instant now = Instant.parse("2031-11-24T20:30:00Z");
        CompanyRepository companies = mock(CompanyRepository.class);
        CompanyAnonymizer anonymizer = mock(CompanyAnonymizer.class);
        Company a = new Company();
        a.setId(1L);
        Company b = new Company();
        b.setId(2L);
        when(companies.findTop50ByDeletedTrueAndAnonymizedAtIsNullAndDeletedAtLessThanEqualOrderByDeletedAtAsc(
                Instant.parse("2026-11-24T20:30:00Z"))).thenReturn(List.of(a, b));
        doThrow(new IllegalStateException("db")).when(anonymizer).anonymize(a);

        CompanyRetentionJob job = new CompanyRetentionJob(companies, anonymizer, Clock.fixed(now, ZoneOffset.UTC));
        ReflectionTestUtils.setField(job, "years", 5);
        ReflectionTestUtils.setField(job, "financeYears", 10);
        Company old = new Company();
        old.setId(3L);
        when(companies.findTop50ByDeletedTrueAndFinanceAnonymizedAtIsNullAndDeletedAtLessThanEqualOrderByDeletedAtAsc(
                Instant.parse("2021-11-24T20:30:00Z"))).thenReturn(List.of(old));
        job.run();

        verify(anonymizer).anonymize(b);
        verify(anonymizer).anonymizeFinance(old);
    }
}
