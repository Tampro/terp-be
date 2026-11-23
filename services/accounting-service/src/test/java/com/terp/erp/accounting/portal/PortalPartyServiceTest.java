package com.terp.erp.accounting.portal;

import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.customer.Customer;
import com.terp.erp.accounting.customer.CustomerRepository;
import com.terp.erp.accounting.note.AccountNote;
import com.terp.erp.accounting.note.AccountNoteRepository;
import com.terp.erp.accounting.note.ENoteKind;
import com.terp.erp.accounting.payment.EPaymentKind;
import com.terp.erp.accounting.payment.Payment;
import com.terp.erp.accounting.payment.PaymentRepository;
import com.terp.erp.accounting.vendor.Vendor;
import com.terp.erp.accounting.vendor.VendorRepository;
import com.terp.erp.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TestLogin.as(companyId) logs in IAM user 5. */
class PortalPartyServiceTest {
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final VendorRepository vendors = mock(VendorRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final AccountNoteRepository notes = mock(AccountNoteRepository.class);
    private final PortalPartyService service = new PortalPartyService(customers, vendors, payments, notes);
    private final Pageable page = PageRequest.of(0, 20);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void linkedCustomer() {
        TestLogin.as(10L, "PORTAL_CLIENT");
        when(customers.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(customer()));
        when(vendors.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.empty());

        var dto = service.me();

        assertThat(dto.customerId()).isEqualTo("7");
        assertThat(dto.customerCode()).isEqualTo("CUST-0001");
        assertThat(dto.customerName()).isEqualTo("Example Buyer Co.");
        assertThat(dto.vendorId()).isNull();
    }

    @Test
    void linkedVendor() {
        TestLogin.as(10L, "PORTAL_VENDOR");
        when(customers.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.empty());
        when(vendors.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(vendor()));

        var dto = service.me();

        assertThat(dto.vendorId()).isEqualTo("8");
        assertThat(dto.vendorCode()).isEqualTo("VEN-0001");
        assertThat(dto.customerId()).isNull();
    }

    @Test
    void notLinkedIsNotFound() {
        TestLogin.as(10L, "PORTAL_CLIENT");
        when(customers.findFirstByCompanyIdAndUserIdAndDeletedFalse(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(vendors.findFirstByCompanyIdAndUserIdAndDeletedFalse(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(service::me).isInstanceOf(ApiException.class).satisfies(e -> {
            assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(((ApiException) e).getErrorCode()).isEqualTo(EAccountingError.ACC_098);
        });
        assertThatThrownBy(() -> service.customerPayments(page)).isInstanceOf(ApiException.class);
        verify(payments, never()).search(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void linkInAnotherCompanyIsIgnored() {
        // User 5 is linked in company 11 only; logged in to company 10 the lookup is by (10, 5).
        TestLogin.as(10L, "PORTAL_CLIENT");
        Customer other = customer();
        other.setCompanyId(11L);
        when(customers.findFirstByCompanyIdAndUserIdAndDeletedFalse(11L, 5L)).thenReturn(Optional.of(other));
        when(customers.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.empty());
        when(vendors.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(service::me).isInstanceOf(ApiException.class);
    }

    @Test
    void superAdminIsLinkedToNothing() {
        TestLogin.superAdmin();
        assertThatThrownBy(service::me).isInstanceOf(ApiException.class);
        verify(customers, never()).findFirstByCompanyIdAndUserIdAndDeletedFalse(any(), any());
    }

    @Test
    void clientListsOnlyItsOwnCustomerRows() {
        TestLogin.as(10L, "PORTAL_CLIENT");
        when(customers.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(customer()));
        Payment p = new Payment();
        p.setId(30L);
        p.setNumber("CP-0001");
        p.setNotes("internal remark");
        when(payments.search(eq(10L), eq(EPaymentKind.CUSTOMER), eq(7L), isNull(), isNull(), isNull(), isNull(),
                eq(page))).thenReturn(new PageImpl<>(List.of(p)));
        AccountNote n = new AccountNote();
        n.setId(40L);
        n.setNumber("CN-0001");
        when(notes.issuedToParty(10L, ENoteKind.CREDIT, 7L, page)).thenReturn(new PageImpl<>(List.of(n)));

        assertThat(service.customerPayments(page).getContent()).singleElement()
                .satisfies(d -> assertThat(d.number()).isEqualTo("CP-0001"));
        assertThat(service.creditNotes(page).getContent()).singleElement()
                .satisfies(d -> assertThat(d.id()).isEqualTo("40"));
    }

    @Test
    void vendorListsOnlyItsOwnVendorRows() {
        TestLogin.as(10L, "PORTAL_VENDOR");
        when(vendors.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(vendor()));
        when(payments.search(eq(10L), eq(EPaymentKind.VENDOR), eq(8L), isNull(), isNull(), isNull(), isNull(),
                eq(page))).thenReturn(new PageImpl<>(List.of()));
        when(notes.issuedToParty(10L, ENoteKind.DEBIT, 8L, page)).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.vendorPayments(page)).isEmpty();
        assertThat(service.debitNotes(page)).isEmpty();
        verify(payments).search(eq(10L), eq(EPaymentKind.VENDOR), eq(8L), isNull(), isNull(), isNull(), isNull(),
                eq(page));
        // A vendor-only user has no customer, so the client lists answer 404.
        when(customers.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.creditNotes(page)).isInstanceOf(ApiException.class);
    }

    private static Customer customer() {
        Customer c = new Customer();
        c.setId(7L);
        c.setCompanyId(10L);
        c.setCustomerCode("CUST-0001");
        c.setCompanyName("Example Buyer Co.");
        c.setUserId(5L);
        return c;
    }

    private static Vendor vendor() {
        Vendor v = new Vendor();
        v.setId(8L);
        v.setCompanyId(10L);
        v.setVendorCode("VEN-0001");
        v.setCompanyName("Example Supplier Co.");
        v.setUserId(5L);
        return v;
    }
}
