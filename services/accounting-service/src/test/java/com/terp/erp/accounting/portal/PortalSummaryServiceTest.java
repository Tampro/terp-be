package com.terp.erp.accounting.portal;

import com.terp.erp.accounting.TestLogin;
import com.terp.erp.accounting.customer.Customer;
import com.terp.erp.accounting.customer.CustomerRepository;
import com.terp.erp.accounting.note.AccountNoteRepository;
import com.terp.erp.accounting.note.ENoteKind;
import com.terp.erp.accounting.payment.EPaymentKind;
import com.terp.erp.accounting.payment.PaymentRepository;
import com.terp.erp.accounting.vendor.Vendor;
import com.terp.erp.accounting.vendor.VendorRepository;
import com.terp.erp.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TestLogin.as(companyId) logs in IAM user 5. */
class PortalSummaryServiceTest {
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final VendorRepository vendors = mock(VendorRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final AccountNoteRepository notes = mock(AccountNoteRepository.class);
    private final PortalSummaryService service = new PortalSummaryService(customers, vendors, payments, notes,
            Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clientFiguresOfMyCustomerOnly() {
        TestLogin.as(10L, "PORTAL_CLIENT");
        Customer c = new Customer();
        c.setId(7L);
        c.setCompanyId(10L);
        when(customers.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(c));
        when(payments.clearedByMonth(10L, EPaymentKind.CUSTOMER, 7L, LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31))).thenReturn(List.<Object[]>of(new Object[]{2, new BigDecimal("40")}));
        when(payments.sumCleared(10L, EPaymentKind.CUSTOMER, 7L)).thenReturn(new BigDecimal("140"));
        when(notes.issuedSummary(10L, ENoteKind.CREDIT, 7L))
                .thenReturn(List.<Object[]>of(new Object[]{new BigDecimal("15"), 2L}));

        var s = service.client(2025);

        assertThat(s.year()).isEqualTo(2025);
        assertThat(s.totalSettled()).isEqualByComparingTo("140");
        assertThat(s.monthly()).hasSize(12);
        assertThat(s.monthly().get(1).amount()).isEqualByComparingTo("40");
        assertThat(s.monthly().get(0).amount()).isEqualByComparingTo("0");
        assertThat(s.notesTotal()).isEqualByComparingTo("15");
        assertThat(s.notesCount()).isEqualTo(2);
    }

    @Test
    void vendorUsesDebitNotesAndVendorPayments() {
        TestLogin.as(10L, "PORTAL_VENDOR");
        Vendor v = new Vendor();
        v.setId(3L);
        v.setCompanyId(10L);
        when(vendors.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(v));
        when(payments.clearedByMonth(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(notes.issuedSummary(10L, ENoteKind.DEBIT, 3L)).thenReturn(List.of());

        var s = service.vendor(null);

        assertThat(s.year()).isEqualTo(2026);
        assertThat(s.totalSettled()).isEqualByComparingTo("0");
        assertThat(s.notesCount()).isZero();
        verify(payments).sumCleared(10L, EPaymentKind.VENDOR, 3L);
    }

    @Test
    void notLinkedIs404() {
        TestLogin.as(10L, "PORTAL_CLIENT");
        when(customers.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.client(null)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        TestLogin.superAdmin();
        assertThatThrownBy(() -> service.vendor(null)).isInstanceOf(ApiException.class);
        verify(payments, never()).sumCleared(any(), any(), any());
    }

    @Test
    void handlersNeedThePortalPermission() throws Exception {
        assertThat(PortalControllers.ClientPortalController.class.getMethod("summary", Integer.class)
                .getAnnotation(PreAuthorize.class).value()).contains("PORTAL_CLIENT");
        assertThat(PortalControllers.VendorPortalController.class.getMethod("summary", Integer.class)
                .getAnnotation(PreAuthorize.class).value()).contains("PORTAL_VENDOR");
    }
}
