package com.terp.erp.iam.saas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.model.Currency;
import com.terp.erp.iam.repository.CurrencyRepository;
import com.terp.erp.iam.saas.CurrencyService.CurrencyForm;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrencyServiceTest {
    private CurrencyRepository repo;
    private CurrencyService service;
    private Currency vnd;

    @BeforeEach
    void setUp() {
        repo = mock(CurrencyRepository.class);
        service = new CurrencyService(repo);
        vnd = currency(1L, "VND", true);
        when(repo.findById(1L)).thenReturn(Optional.of(vnd));
        when(repo.findFirstByDefaultCurrencyTrue()).thenReturn(Optional.of(vnd));
        when(repo.save(any(Currency.class))).thenAnswer(inv -> {
            Currency c = inv.getArgument(0);
            c.setId(9L);
            return c;
        });
    }

    @Test
    void settingANewDefaultMovesTheFlag() {
        var usd = service.create(new CurrencyForm("US Dollar", "USD", "$", null, true));
        assertThat(usd.defaultCurrency()).isTrue();
        assertThat(vnd.isDefaultCurrency()).isFalse();
    }

    @Test
    void firstCurrencyBecomesDefault() {
        when(repo.findFirstByDefaultCurrencyTrue()).thenReturn(Optional.empty());
        assertThat(service.create(new CurrencyForm("Euro", "EUR", "€", null, false)).defaultCurrency()).isTrue();
    }

    @Test
    void defaultCannotBeUnflaggedOrDeleted() {
        assertError(() -> service.update(1L, new CurrencyForm("Dong", "VND", "₫", null, false)), ESaasError.SAAS_025);
        assertError(() -> service.delete(1L), ESaasError.SAAS_024);
        verify(repo, never()).delete(any());
    }

    @Test
    void codeMustBeUnique() {
        when(repo.existsByCode("USD")).thenReturn(true);
        assertError(() -> service.create(new CurrencyForm("US Dollar", "USD", "$", null, false)), ESaasError.SAAS_023);
    }

    @Test
    void nonDefaultIsDeletedPermanently() {
        Currency krw = currency(2L, "KRW", false);
        when(repo.findById(2L)).thenReturn(Optional.of(krw));
        service.delete(2L);
        verify(repo).delete(krw);
    }

    private static Currency currency(Long id, String code, boolean isDefault) {
        Currency c = new Currency();
        c.setId(id);
        c.setCode(code);
        c.setName(code);
        c.setDefaultCurrency(isDefault);
        return c;
    }

    private static void assertError(Runnable call, ESaasError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
