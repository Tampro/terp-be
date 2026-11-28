package com.terp.erp.member.customer;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.member.client.ShopClient;
import com.terp.erp.member.common.EMemberError;
import com.terp.erp.member.customer.MemberService.JoinForm;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-28T03:00:00Z");
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final ShopClient shops = mock(ShopClient.class);
    private final CustomerAddressRepository addresses = mock(CustomerAddressRepository.class);
    private final MemberService service = new MemberService(customers, addresses, shops, Clock.fixed(NOW, ZoneOffset.UTC));
    private final JoinForm ok = new JoinForm(true, true, false, "2026-11", "2026-11");

    @BeforeEach
    void setUp() {
        when(shops.companyOf("express")).thenReturn(10L);
        when(shops.companyOf("other")).thenReturn(20L);
        when(shops.companyOf("closed")).thenThrow(ApiException.notFound(EMemberError.MEM_001));
        when(customers.findFirstByCompanyIdAndKeycloakSubAndStatus(any(), any(), any())).thenReturn(Optional.empty());
        when(customers.findFirstByCompanyIdAndKeycloakSubAndStatusOrderByLeftAtDesc(any(), any(), any())).thenReturn(Optional.empty());
        when(customers.saveAndFlush(any())).thenAnswer(a -> {
            Customer c = a.getArgument(0);
            c.setId(1L);
            return c;
        });
    }

    @Test
    void joinStoresConsentsWithTheShopsCompanyAndTheTokenSubject() {
        MemberService.MemberDto d = service.join(" Express ", "sub-1", new JoinForm(true, true, true, "2026-11", "2026-10"));
        assertThat(d.status()).isEqualTo(ECustomerStatus.ACTIVE);
        assertThat(d.shopSlug()).isEqualTo("express");
        assertThat(d.marketingAgreed()).isTrue();
        verify(customers).saveAndFlush(org.mockito.ArgumentMatchers.argThat(c -> c.getCompanyId() == 10L
                && "sub-1".equals(c.getKeycloakSub()) && "2026-10".equals(c.getPrivacyVersion())
                && NOW.equals(c.getTermsAgreedAt()) && NOW.equals(c.getMarketingAgreedAt())));
    }

    @Test
    void requiredConsentsMustBeTrue() {
        assertCode(() -> service.join("express", "sub-1", new JoinForm(true, false, false, "v", "v")), EMemberError.MEM_005);
        assertCode(() -> service.join("express", "sub-1", new JoinForm(false, true, false, "v", "v")), EMemberError.MEM_005);
        verify(shops, never()).companyOf(any());
    }

    @Test
    void alreadyMemberAlsoOnARace() {
        when(customers.findFirstByCompanyIdAndKeycloakSubAndStatus(10L, "sub-1", ECustomerStatus.ACTIVE))
                .thenReturn(Optional.of(new Customer()));
        assertCode(() -> service.join("express", "sub-1", ok), EMemberError.MEM_002);
        when(customers.findFirstByCompanyIdAndKeycloakSubAndStatus(10L, "sub-2", ECustomerStatus.ACTIVE)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("uk_customer_live")).when(customers).saveAndFlush(any());
        assertCode(() -> service.join("express", "sub-2", ok), EMemberError.MEM_002);
    }

    @Test
    void leftLessThanFiveDaysAgoWaitsThenMayJoin() {
        Customer left = new Customer();
        left.setLeftAt(NOW.minusSeconds(5 * 86400 - 60)); // 1 minute short of 5 days
        when(customers.findFirstByCompanyIdAndKeycloakSubAndStatusOrderByLeftAtDesc(10L, "sub-1", ECustomerStatus.LEFT))
                .thenReturn(Optional.of(left));
        assertCode(() -> service.join("express", "sub-1", ok), EMemberError.MEM_003);
        left.setLeftAt(NOW.minusSeconds(5 * 86400));
        assertThat(service.join("express", "sub-1", ok).status()).isEqualTo(ECustomerStatus.ACTIVE);
    }

    @Test
    void anotherShopIsAnotherMembership() {
        Customer mine = new Customer();
        mine.setId(5L);
        mine.setStatus(ECustomerStatus.ACTIVE);
        when(customers.findFirstByCompanyIdAndKeycloakSubAndStatus(10L, "sub-1", ECustomerStatus.ACTIVE)).thenReturn(Optional.of(mine));
        assertThat(service.get("express", "sub-1").customerId()).isEqualTo("5");
        assertCode(() -> service.get("other", "sub-1"), EMemberError.MEM_004);
    }

    @Test
    void leaveKeepsTheRowAndFreesTheKey() {
        Customer mine = new Customer();
        mine.setId(5L);
        mine.setStatus(ECustomerStatus.ACTIVE);
        when(customers.findFirstByCompanyIdAndKeycloakSubAndStatus(10L, "sub-1", ECustomerStatus.ACTIVE)).thenReturn(Optional.of(mine));
        service.leave("express", "sub-1");
        assertThat(mine.getStatus()).isEqualTo(ECustomerStatus.LEFT);
        assertThat(mine.getLeftAt()).isEqualTo(NOW);
        assertThat(mine.getLiveKey()).isEqualTo(5L);
        verify(addresses).deleteOfCustomer(5L);
        assertCode(() -> service.leave("express", "sub-9"), EMemberError.MEM_004);
    }

    @Test
    void unavailableShopIsMem001() {
        assertCode(() -> service.get("closed", "sub-1"), EMemberError.MEM_001);
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
