package com.terp.erp.commerce.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalesInvoicePaymentRepository extends JpaRepository<SalesInvoicePayment, Long> {
    Optional<SalesInvoicePayment> findByInvoiceIdAndPaymentId(Long invoiceId, Long paymentId);
}
