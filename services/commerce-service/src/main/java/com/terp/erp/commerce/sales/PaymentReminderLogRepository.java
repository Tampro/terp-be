package com.terp.erp.commerce.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface PaymentReminderLogRepository extends JpaRepository<PaymentReminderLog, Long> {
    boolean existsByInvoiceIdAndReminderDate(Long invoiceId, LocalDate reminderDate);
}
