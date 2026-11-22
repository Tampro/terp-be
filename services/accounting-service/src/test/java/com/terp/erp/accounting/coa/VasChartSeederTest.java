package com.terp.erp.accounting.coa;

import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.LedgerSetup;
import com.terp.erp.accounting.ledger.LedgerSetupRepository;
import com.terp.erp.accounting.ledger.SystemAccountMapping;
import com.terp.erp.accounting.ledger.SystemAccountMappingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VasChartSeederTest {
    private final LedgerSetupRepository setups = mock(LedgerSetupRepository.class);
    private final AccountTypeRepository types = mock(AccountTypeRepository.class);
    private final ChartAccountRepository accounts = mock(ChartAccountRepository.class);
    private final SystemAccountMappingRepository mappings = mock(SystemAccountMappingRepository.class);
    private final VasChartSeeder seeder = new VasChartSeeder(setups, types, accounts, mappings);

    @Test
    void seedsChartAndMapsEverySystemKeyOnce() {
        LedgerSetup setup = new LedgerSetup();
        setup.setCompanyId(10L);
        when(setups.lockByCompanyId(10L)).thenReturn(Optional.of(setup));
        AtomicLong ids = new AtomicLong();
        when(types.save(any())).thenAnswer(i -> {
            AccountType t = i.getArgument(0);
            t.setId(ids.incrementAndGet());
            return t;
        });
        Map<Long, ChartAccount> saved = new HashMap<>();
        when(accounts.save(any())).thenAnswer(i -> {
            ChartAccount a = i.getArgument(0);
            a.setId(ids.incrementAndGet());
            saved.put(a.getId(), a);
            return a;
        });

        seeder.ensureSeeded(10L);

        assertThat(saved).hasSizeGreaterThanOrEqualTo(45);
        assertThat(saved.values()).allMatch(a -> a.getCompanyId().equals(10L) && a.getAccountTypeId() != null);
        ArgumentCaptor<SystemAccountMapping> captor = ArgumentCaptor.forClass(SystemAccountMapping.class);
        verify(mappings, atLeastOnce()).save(captor.capture());
        List<SystemAccountMapping> rows = new ArrayList<>(captor.getAllValues());
        assertThat(rows).hasSize(ESystemAccount.values().length);
        for (SystemAccountMapping m : rows) {
            ChartAccount a = saved.get(m.getAccountId());
            assertThat(a).as(m.getSystemKey().name()).isNotNull();
            assertThat(a.getCode()).isEqualTo(m.getSystemKey().defaultCode());
        }
        ChartAccount child = saved.values().stream().filter(a -> a.getCode().equals("33311")).findFirst().orElseThrow();
        assertThat(saved.get(child.getParentId()).getCode()).isEqualTo("3331");
        assertThat(setup.isSeeded()).isTrue();

        // Second use: nothing more is written.
        seeder.ensureSeeded(10L);
        verify(mappings, times(ESystemAccount.values().length)).save(any());
    }

    @Test
    void alreadySeededCompanyIsLeftAlone() {
        LedgerSetup setup = new LedgerSetup();
        setup.setSeeded(true);
        when(setups.lockByCompanyId(10L)).thenReturn(Optional.of(setup));
        seeder.ensureSeeded(10L);
        verify(accounts, never()).save(any());
        verify(mappings, never()).save(any());
    }
}
