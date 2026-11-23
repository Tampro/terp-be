package com.terp.erp.accounting.customer;

import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.customer.CustomerService.AddressForm;
import com.terp.erp.accounting.customer.CustomerService.CustomerForm;
import com.terp.erp.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceTest {
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerCodeSequenceRepository sequences = mock(CustomerCodeSequenceRepository.class);
    private final com.terp.erp.outbox.OutboxPublisher outbox = org.mockito.Mockito.mock(com.terp.erp.outbox.OutboxPublisher.class);
    private final CustomerService service = new CustomerService(customers, sequences, outbox);
    private final CustomerCodeSequence seq = new CustomerCodeSequence();
    private final AddressForm billing = new AddressForm("Example Trading Co.", "1 Example Street", null,
            "Testville", "Test State", "Testland", "00000");

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "CUSTOMER_MANAGE");
        seq.setCompanyId(10L);
        when(sequences.lockByCompanyId(10L)).thenReturn(Optional.of(seq));
        when(customers.save(any())).thenAnswer(i -> {
            Customer c = i.getArgument(0);
            c.setId(7L);
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void codesFollowTheCompanySequence() {
        assertThat(service.create(null, form(null, true, null)).customerCode()).isEqualTo("CUST-0001");
        assertThat(service.create(null, form(null, true, null)).customerCode()).isEqualTo("CUST-0002");
        assertThat(seq.getLastNumber()).isEqualTo(2);
        verify(sequences, org.mockito.Mockito.times(2)).ensureRow(10L);
    }

    @Test
    void shippingCopiedFromBillingWhenSame() {
        AddressForm other = new AddressForm("Other", "9 Other Road", null, "Elsewhere", null, null, null);
        var dto = service.create(null, form(null, true, other));
        {
            var ev = org.mockito.ArgumentCaptor.forClass(Object.class);
            org.mockito.Mockito.verify(outbox, org.mockito.Mockito.atLeastOnce()).publish(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq("accounting.customer.created"), org.mockito.ArgumentMatchers.eq("CUSTOMER"),
                    org.mockito.ArgumentMatchers.any(), ev.capture());
            @SuppressWarnings("unchecked") var p = (java.util.Map<String, Object>) ev.getValue();
            org.assertj.core.api.Assertions.assertThat(p).containsKeys("customerId", "title", "customerCode", "companyName");
        }
        assertThat(dto.shipping().address()).isEqualTo("1 Example Street");
        assertThat(dto.shipping().city()).isEqualTo("Testville");
        assertThat(dto.shippingSameAsBilling()).isTrue();
        assertThat(service.create(null, form(null, false, other)).shipping().address()).isEqualTo("9 Other Road");
    }

    @Test
    void duplicateUserIdRefused() {
        when(customers.existsByCompanyIdAndUserIdAndDeletedFalseAndIdNot(eq(10L), eq(55L), anyLong()))
                .thenReturn(true);
        assertCode(() -> service.create(null, form(55L, true, null)), EAccountingError.ACC_003);
        verify(sequences, never()).ensureRow(any());
    }

    @Test
    void otherCompanysCustomerIsNotFound() {
        Customer c = new Customer();
        c.setId(7L);
        c.setCompanyId(11L);
        c.setCustomerCode("CUST-0001");
        when(customers.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(c));
        assertCode(() -> service.get(7L), EAccountingError.ACC_002);
        assertCode(() -> service.update(7L, form(null, true, null)), EAccountingError.ACC_002);
        assertCode(() -> service.delete(7L), EAccountingError.ACC_002);
        assertThat(c.isDeleted()).isFalse();
        assertThatThrownBy(() -> service.get(7L)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    @Test
    void superAdminMustNameACompany() {
        TestLogin.superAdmin();
        assertCode(() -> service.create(null, form(null, true, null)), EAccountingError.ACC_001);
    }

    private CustomerForm form(Long userId, boolean same, AddressForm shipping) {
        return new CustomerForm("Example Trading Co.", "Test Contact", "buyer@example.com", "(000) 000-0000",
                "0000000000", "Net 30", userId, billing, same, shipping, null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EAccountingError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
