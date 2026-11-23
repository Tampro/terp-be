package com.terp.erp.commerce.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** One reminder per invoice per day (unique invoice_id + reminder_date), written with its outbox event. */
@Getter
@Setter
@Entity
@Table(name = "payment_reminder_log")
public class PaymentReminderLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
