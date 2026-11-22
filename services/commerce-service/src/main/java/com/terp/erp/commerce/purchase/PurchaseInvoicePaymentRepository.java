package com.terp.erp.commerce.purchase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PurchaseInvoicePaymentRepository extends JpaRepository<PurchaseInvoicePayment, Long> {
    Optional<PurchaseInvoicePayment> findByInvoiceIdAndPaymentId(Long invoiceId, Long paymentId);
}
