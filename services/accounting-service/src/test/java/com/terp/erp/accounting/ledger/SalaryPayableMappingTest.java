package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.coa.VasChartSeeder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalaryPayableMappingTest {
    private final SystemAccountMappingRepository mappings = mock(SystemAccountMappingRepository.class);
    private final ChartAccountRepository accounts = mock(ChartAccountRepository.class);
    private final AccountMapping mapping = new AccountMapping(mappings, accounts, mock(VasChartSeeder.class));

    @Test
    void companySeededBeforeTheKeyGetsItMappedTo334OnFirstUse() {
        ChartAccount a334 = new ChartAccount();
        a334.setId(334L);
        a334.setCompanyId(10L);
        a334.setCode("334");
        a334.setActive(true);
        when(mappings.findByCompanyIdAndSystemKey(10L, ESystemAccount.SALARY_PAYABLE)).thenReturn(Optional.empty());
        when(accounts.findByCompanyIdAndCode(10L, "334")).thenReturn(Optional.of(a334));
        when(accounts.findById(334L)).thenReturn(Optional.of(a334));
        when(mappings.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

        assertThat(mapping.require(10L, ESystemAccount.SALARY_PAYABLE)).isEqualTo(334L);
        ArgumentCaptor<SystemAccountMapping> saved = ArgumentCaptor.forClass(SystemAccountMapping.class);
        verify(mappings).save(saved.capture());
        assertThat(saved.getValue().getSystemKey()).isEqualTo(ESystemAccount.SALARY_PAYABLE);
        assertThat(saved.getValue().getAccountId()).isEqualTo(334L);
    }
}
