package com.terp.erp.commerce.portal;

import com.terp.erp.commerce.TestLogin;
import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.commerce.client.RemoteApiException;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.common.EPurchaseError;
import com.terp.erp.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalPartyResolverTest {
    private final AccountingClient accounting = mock(AccountingClient.class);
    private final PortalPartyResolver resolver = new PortalPartyResolver(accounting);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cachesASuccessPerCompanyAndUser() {
        TestLogin.as(10L, "PORTAL_CLIENT");
        when(accounting.myParty()).thenReturn(new AccountingClient.Party("5", "CUST-0005", "Test Customer",
                null, null, null));
        assertThat(resolver.customer()).isEqualTo(new PortalPartyResolver.Mine(10L, 5L, "Test Customer"));
        resolver.customer();
        verify(accounting, times(1)).myParty();
        // linked as a customer only: the vendor side answers 404 of this service
        assertThatThrownBy(resolver::vendor)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EPurchaseError.PUR_018);

        TestLogin.as(11L, "PORTAL_CLIENT");
        resolver.customer();
        verify(accounting, times(2)).myParty();
    }

    @Test
    void neverCachesAFailure() {
        TestLogin.as(10L, "PORTAL_VENDOR");
        when(accounting.myParty())
                .thenThrow(new RemoteApiException(HttpStatus.NOT_FOUND, "ACC_098", "not linked"))
                .thenReturn(new AccountingClient.Party(null, null, null, "3", "VEN-0003", "Test Vendor"));
        assertThatThrownBy(resolver::vendor).isInstanceOf(RemoteApiException.class);
        assertThat(resolver.vendor().partyId()).isEqualTo(3L);
        verify(accounting, times(2)).myParty();
        assertThatThrownBy(resolver::customer)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ECommerceError.SAL_026);
    }
}
