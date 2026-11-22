package com.terp.erp.support.ticket;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ticket numbers TKT-000001, TKT-000002 … per company. The company's sequence row is locked (FOR UPDATE) until the
 * caller's transaction ends, so two tickets created at once never get the same number; a rolled-back create leaves
 * no gap because the increment rolls back with it.
 */
@Component
public class TicketNumbers {
    @PersistenceContext
    private EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Long companyId) {
        em.createNativeQuery("INSERT INTO ticket_sequence (company_id, next_value) VALUES (:c, 1)"
                        + " ON DUPLICATE KEY UPDATE next_value = next_value")
                .setParameter("c", companyId).executeUpdate();
        Number n = (Number) em.createNativeQuery(
                        "SELECT next_value FROM ticket_sequence WHERE company_id = :c FOR UPDATE")
                .setParameter("c", companyId).getSingleResult();
        em.createNativeQuery("UPDATE ticket_sequence SET next_value = next_value + 1 WHERE company_id = :c")
                .setParameter("c", companyId).executeUpdate();
        return format(n.longValue());
    }

    static String format(long n) {
        return String.format("TKT-%06d", n);
    }
}
