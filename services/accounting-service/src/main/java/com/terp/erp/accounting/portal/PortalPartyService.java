package com.terp.erp.accounting.portal;

import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.customer.Customer;
import com.terp.erp.accounting.customer.CustomerRepository;
import com.terp.erp.accounting.note.AccountNote;
import com.terp.erp.accounting.note.AccountNoteRepository;
import com.terp.erp.accounting.note.ENoteKind;
import com.terp.erp.accounting.note.ENoteStatus;
import com.terp.erp.accounting.payment.EPaymentKind;
import com.terp.erp.accounting.payment.EPaymentStatus;
import com.terp.erp.accounting.payment.Payment;
import com.terp.erp.accounting.payment.PaymentRepository;
import com.terp.erp.accounting.vendor.Vendor;
import com.terp.erp.accounting.vendor.VendorRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The customer / vendor a portal user is linked to ({@code Customer.userId} / {@code Vendor.userId}) and the
 * accounting rows of that party only. The party always comes from the caller, never from the request, so a portal
 * user cannot name another party's id.
 */
@Service
@RequiredArgsConstructor
public class PortalPartyService {
    private final CustomerRepository customers;
    private final VendorRepository vendors;
    private final PaymentRepository payments;
    private final AccountNoteRepository notes;

    @Schema(description = "The customer and / or vendor linked to the caller; ids are strings, null when not linked")
    public record PartyDto(String customerId, String customerCode, String customerName,
                           String vendorId, String vendorCode, String vendorName) {
    }

    /** What a customer / vendor sees of a payment: no internal notes, bank account or clearing user. */
    public record PortalPaymentDto(String id, String number, LocalDate paymentDate, BigDecimal amount,
                                   BigDecimal allocatedTotal, BigDecimal notesTotal, String reference,
                                   EPaymentStatus status) {
        static PortalPaymentDto of(Payment p) {
            return new PortalPaymentDto(String.valueOf(p.getId()), p.getNumber(), p.getPaymentDate(), p.getAmount(),
                    p.getAllocatedTotal(), p.getNotesTotal(), p.getReference(), p.getStatus());
        }
    }

    /** What a customer / vendor sees of a credit / debit note: no internal notes or approver. */
    public record PortalNoteDto(String id, String number, LocalDate noteDate, String returnNumber, BigDecimal total,
                                BigDecimal taxAmount, BigDecimal balance, ENoteStatus status) {
        static PortalNoteDto of(AccountNote n) {
            return new PortalNoteDto(String.valueOf(n.getId()), n.getNumber(), n.getNoteDate(), n.getReturnNumber(),
                    n.getTotal(), n.getTaxAmount(), n.getBalance(), n.getStatus());
        }
    }

    @Transactional(readOnly = true)
    public PartyDto me() {
        Optional<Customer> c = myCustomer();
        Optional<Vendor> v = myVendor();
        if (c.isEmpty() && v.isEmpty()) {
            throw ApiException.notFound(EAccountingError.ACC_098);
        }
        return new PartyDto(c.map(x -> String.valueOf(x.getId())).orElse(null),
                c.map(Customer::getCustomerCode).orElse(null), c.map(Customer::getCompanyName).orElse(null),
                v.map(x -> String.valueOf(x.getId())).orElse(null),
                v.map(Vendor::getVendorCode).orElse(null), v.map(Vendor::getCompanyName).orElse(null));
    }

    @Transactional(readOnly = true)
    public Page<PortalPaymentDto> customerPayments(Pageable pageable) {
        Customer c = myCustomer().orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_098));
        return payments.search(c.getCompanyId(), EPaymentKind.CUSTOMER, c.getId(), null, null, null, null, pageable)
                .map(PortalPaymentDto::of);
    }

    @Transactional(readOnly = true)
    public Page<PortalNoteDto> creditNotes(Pageable pageable) {
        Customer c = myCustomer().orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_098));
        return notes.issuedToParty(c.getCompanyId(), ENoteKind.CREDIT, c.getId(), pageable).map(PortalNoteDto::of);
    }

    @Transactional(readOnly = true)
    public Page<PortalPaymentDto> vendorPayments(Pageable pageable) {
        Vendor v = myVendor().orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_098));
        return payments.search(v.getCompanyId(), EPaymentKind.VENDOR, v.getId(), null, null, null, null, pageable)
                .map(PortalPaymentDto::of);
    }

    @Transactional(readOnly = true)
    public Page<PortalNoteDto> debitNotes(Pageable pageable) {
        Vendor v = myVendor().orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_098));
        return notes.issuedToParty(v.getCompanyId(), ENoteKind.DEBIT, v.getId(), pageable).map(PortalNoteDto::of);
    }

    // A super admin has no company / user id, so is linked to nothing.
    private Optional<Customer> myCustomer() {
        ErpPrincipal p = CurrentUser.get();
        if (p.companyId() == null || p.userId() == null) {
            return Optional.empty();
        }
        return customers.findFirstByCompanyIdAndUserIdAndDeletedFalse(p.companyId(), p.userId());
    }

    private Optional<Vendor> myVendor() {
        ErpPrincipal p = CurrentUser.get();
        if (p.companyId() == null || p.userId() == null) {
            return Optional.empty();
        }
        return vendors.findFirstByCompanyIdAndUserIdAndDeletedFalse(p.companyId(), p.userId());
    }
}
