package com.terp.erp.member.common;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Only the named company is touched, and a second run changes nothing more (subjects already anon-*). */
class MemberAnonymizerTest {
    @Test
    void deletesAddressesAndAnonymisesSubjectsOfTheCompanyOnly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MemberAnonymizer anonymizer = new MemberAnonymizer(jdbc);
        anonymizer.anonymize(10L);
        anonymizer.anonymize(10L);
        verify(jdbc, times(2)).update(MemberAnonymizer.DELETE_ADDRESSES, 10L);
        verify(jdbc, times(2)).update(MemberAnonymizer.ANONYMIZE_SUBJECTS, 10L);
        assertThat(MemberAnonymizer.ANONYMIZE_SUBJECTS).contains("company_id = ?", "NOT LIKE 'anon-%'");
        assertThat(MemberAnonymizer.DELETE_ADDRESSES).contains("company_id = ?");
    }
}
