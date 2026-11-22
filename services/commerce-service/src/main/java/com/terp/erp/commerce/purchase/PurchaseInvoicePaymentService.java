package com.terp.erp.commerce.purchase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.commerce.common.EPurchaseError;
import com.terp.erp.commerce.common.InvoicePaymentForm;
import com.terp.erp.commerce.purchase.PurchaseInvoiceService.InvoiceDetail;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Cleared vendor payments of accounting-service applied to purchase invoices (ERP manual 12.4). Idempotent per
 * (invoice, paymentId): accounting retries a clear that failed half-way, and an amount already taken off the balance
 * is never taken twice.
 */
@Service
@RequiredArgsConstructor
public class PurchaseInvoicePaymentService {
    private final PurchaseInvoiceRepository documents;
    private final PurchaseInvoicePaymentRepository payments;
    private final Clock clock;

    @Transactional
    public InvoiceDetail apply(Long invoiceId, InvoicePaymentForm f) {
        PurchaseInvoice d = documents.lockById(invoiceId)
                .orElseThrow(() -> ApiException.notFound(EPurchaseError.PUR_001));
        TenantGuard.assertSameCompany(d.getCompanyId(), EPurchaseError.PUR_001);
        Optional<PurchaseInvoicePayment> done = payments.findByInvoiceIdAndPaymentId(d.getId(), f.paymentId());
        if (done.isPresent()) {
            if (done.get().getAmount().compareTo(f.amount()) != 0) {
                throw ApiException.badRequest(EPurchaseError.PUR_016, f.paymentId());
            }
            return InvoiceDetail.of(d, LocalDate.now(clock));
        }
        if (d.getStatus() != PurchaseInvoice.EStatus.POSTED && d.getStatus() != PurchaseInvoice.EStatus.PARTIAL) {
            throw ApiException.badRequest(EPurchaseError.PUR_014);
        }
        if (f.amount().compareTo(d.getBalance()) > 0) {
            throw ApiException.badRequest(EPurchaseError.PUR_015, f.amount().toPlainString(),
                    d.getBalance().toPlainString());
        }
        PurchaseInvoicePayment p = new PurchaseInvoicePayment();
        p.setCompanyId(d.getCompanyId());
        p.setInvoiceId(d.getId());
        p.setPaymentId(f.paymentId());
        p.setAmount(f.amount());
        payments.save(p);
        d.setPaidAmount(d.getPaidAmount().add(f.amount()));
        d.setBalance(d.getTotal().subtract(d.getPaidAmount()));
        d.setStatus(d.getBalance().compareTo(BigDecimal.ZERO) <= 0 ? PurchaseInvoice.EStatus.PAID
                : PurchaseInvoice.EStatus.PARTIAL);
        return InvoiceDetail.of(d, LocalDate.now(clock));
    }
}
