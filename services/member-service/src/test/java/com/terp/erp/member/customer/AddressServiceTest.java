package com.terp.erp.member.customer;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.exception.ErrorCode;
import com.terp.erp.member.client.StorefrontClient;
import com.terp.erp.member.common.EMemberError;
import com.terp.erp.member.common.FieldCipher;
import com.terp.erp.member.common.FieldCipherTest;
import com.terp.erp.member.customer.AddressService.AddressForm;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddressServiceTest {
    private final MemberService members = mock(MemberService.class);
    private final CustomerAddressRepository addresses = mock(CustomerAddressRepository.class);
    private final FieldCipher cipher = FieldCipherTest.cipher();
    private final StorefrontClient storefront = mock(StorefrontClient.class);
    private final AddressService service = new AddressService(members, addresses, cipher, storefront);
    private final List<CustomerAddress> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Customer me = new Customer();
        me.setId(5L);
        me.setCompanyId(10L);
        when(members.requireActive(anyString(), anyString())).thenReturn(me);
        when(addresses.ofCustomer(5L)).thenAnswer(a -> rows.stream()
                .sorted((x, y) -> x.isDefault() != y.isDefault() ? (x.isDefault() ? -1 : 1) : Long.compare(y.getId(), x.getId())).toList());
        when(addresses.countByCustomerId(5L)).thenAnswer(a -> (long) rows.size());
        when(addresses.saveAndFlush(any())).thenAnswer(a -> {
            CustomerAddress r = a.getArgument(0);
            if (r.getId() == null) {
                r.setId((long) rows.size() + 100);
                rows.add(r);
            }
            return r;
        });
        when(addresses.findByIdAndCustomerId(any(), any())).thenAnswer(a ->
                rows.stream().filter(r -> r.getId().equals(a.getArgument(0)) && Long.valueOf(5L).equals(a.getArgument(1))).findFirst());
        when(storefront.servingStore(any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void firstAddressIsDefaultAndThePhoneIsStoredEncrypted() {
        AddressService.AddressDto d = service.create("express", "sub-1", form("010-1234-5678", false));
        assertThat(d.isDefault()).isTrue();
        assertThat(d.phone()).isEqualTo("01012345678");
        assertThat(d.servingStore()).isNull();
        ArgumentCaptor<CustomerAddress> saved = ArgumentCaptor.forClass(CustomerAddress.class);
        verify(addresses).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPhoneEnc()).doesNotContain("01012345678");
        assertThat(cipher.decrypt(saved.getValue().getPhoneEnc())).isEqualTo("01012345678");
        assertThat(saved.getValue().getCompanyId()).isEqualTo(10L);
    }

    @Test
    void isDefaultMovesTheDefaultAndDeletingItPromotesTheNewest() {
        service.create("express", "sub-1", form("01011112222", false));      // 100 default
        service.create("express", "sub-1", form("01011113333", false));      // 101
        service.create("express", "sub-1", form("01011114444", true));       // 102 default
        assertThat(rows).filteredOn(CustomerAddress::isDefault).extracting(CustomerAddress::getId).containsExactly(102L);
        service.makeDefault("express", "sub-1", 100L);
        assertThat(rows).filteredOn(CustomerAddress::isDefault).extracting(CustomerAddress::getId).containsExactly(100L);
        CustomerAddress def = rows.get(0);
        org.mockito.Mockito.doAnswer(a -> rows.remove(def)).when(addresses).delete(def);
        service.delete("express", "sub-1", 100L);
        assertThat(rows).filteredOn(CustomerAddress::isDefault).extracting(CustomerAddress::getId).containsExactly(102L);
    }

    @Test
    void eleventhAddressIsRefused() {
        for (int i = 0; i < 10; i++) {
            service.create("express", "sub-1", form("0101111000" + i, false));
        }
        assertCode(() -> service.create("express", "sub-1", form("01099998888", false)), EMemberError.MEM_008);
    }

    @Test
    void anotherMembersAddressIsNotFound() {
        assertCode(() -> service.replace("express", "sub-1", 999L, form("01011112222", false)), EMemberError.MEM_007);
        assertCode(() -> service.delete("express", "sub-1", 999L), EMemberError.MEM_007);
    }

    @Test
    void phoneMustHaveNineToElevenDigits() {
        assertCode(() -> service.create("express", "sub-1", form("12-34", false)), ECommonError.COMMON_001);
        verify(addresses, never()).saveAndFlush(any());
    }

    @Test
    void servingStoreIsAddedWhenTheShopDeliversThere() {
        when(storefront.servingStore(any(), any(), any()))
                .thenReturn(Optional.of(new StorefrontClient.ServingStore("7", "Express Gasan", true, null)));
        assertThat(service.create("Express", "sub-1", form("01011112222", false)).servingStore().id()).isEqualTo("7");
    }

    private static AddressForm form(String phone, boolean isDefault) {
        return new AddressForm("Home", "Hong Gildong", phone, "08502", "106 Gasan-ro", null, new BigDecimal("37.4752"),
                new BigDecimal("126.8940"), null, isDefault);
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
