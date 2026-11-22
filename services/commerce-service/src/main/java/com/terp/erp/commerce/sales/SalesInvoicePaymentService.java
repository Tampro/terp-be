package com.terp.erp.commerce.sales;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.commerce.common.ECommerceError;
import com.terp.erp.commerce.common.InvoicePaymentForm;
import com.terp.erp.commerce.sales.SalesDtos.DocumentDetail;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Cleared customer payments of accounting-service applied to sales invoices (ERP manual 12.4). Idempotent per
 * (invoice, paymentId): accounting retries a clear that failed half-way, and an amount already taken off the balance
 * is never taken twice.
 */
@Service
@RequiredArgsConstructor
public class SalesInvoicePaymentService {
    private final SalesDocumentRepository documents;
    private final SalesInvoicePaymentRepository payments;
    private final Clock clock;

    @Transactional
    public DocumentDetail apply(Long invoiceId, InvoicePaymentForm f) {
        SalesDocument d = documents.lockById(invoiceId).filter(x -> x.getKind() == EDocumentKind.INVOICE)
                .orElseThrow(() -> ApiException.notFound(ECommerceError.SAL_001));
        TenantGuard.assertSameCompany(d.getCompanyId(), ECommerceError.SAL_001);
        Optional<SalesInvoicePayment> done = payments.findByInvoiceIdAndPaymentId(d.getId(), f.paymentId());
        if (done.isPresent()) {
            if (done.get().getAmount().compareTo(f.amount()) != 0) {
                throw ApiException.badRequest(ECommerceError.SAL_023, f.paymentId());
            }
            return DocumentDetail.of(d, LocalDate.now(clock));
        }
        if (d.getStatus() != EDocumentStatus.POSTED && d.getStatus() != EDocumentStatus.PARTIAL) {
            throw ApiException.badRequest(ECommerceError.SAL_021);
        }
        if (f.amount().compareTo(d.getBalance()) > 0) {
            throw ApiException.badRequest(ECommerceError.SAL_022, f.amount().toPlainString(),
                    d.getBalance().toPlainString());
        }
        SalesInvoicePayment p = new SalesInvoicePayment();
        p.setCompanyId(d.getCompanyId());
        p.setInvoiceId(d.getId());
        p.setPaymentId(f.paymentId());
        p.setAmount(f.amount());
        payments.save(p);
        d.setPaidAmount(d.getPaidAmount().add(f.amount()));
        d.setBalance(d.getTotal().subtract(d.getPaidAmount()));
        d.setStatus(d.getBalance().compareTo(BigDecimal.ZERO) <= 0 ? EDocumentStatus.PAID : EDocumentStatus.PARTIAL);
        return DocumentDetail.of(d, LocalDate.now(clock));
    }
}
