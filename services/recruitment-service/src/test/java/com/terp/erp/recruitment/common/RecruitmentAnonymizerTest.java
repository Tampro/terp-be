package com.terp.erp.recruitment.common;

import com.terp.erp.recruitment.storage.FileStore;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecruitmentAnonymizerTest {
    @Test
    void filesAreDeletedOnlyAfterTheCommit() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FileStore files = mock(FileStore.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(7L), eq(7L))).thenReturn(List.of("k1", "k2"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            new RecruitmentAnonymizer(jdbc, files).anonymize(7L);
            verify(files, never()).delete(anyString());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(files).delete("k1");
            verify(files).delete("k2");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
