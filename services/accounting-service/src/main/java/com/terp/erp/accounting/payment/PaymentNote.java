package com.terp.erp.accounting.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A credit or debit note used in a payment; its balance drops when the payment clears. */
@Getter
@Setter
@Entity
@Table(name = "payment_note")
public class PaymentNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
}
