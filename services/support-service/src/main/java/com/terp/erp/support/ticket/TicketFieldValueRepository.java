package com.terp.erp.support.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketFieldValueRepository extends JpaRepository<TicketFieldValue, Long> {
    List<TicketFieldValue> findByTicketIdOrderByIdAsc(Long ticketId);

    @Modifying
    @Query("delete from TicketFieldValue v where v.ticketId = :ticketId")
    void deleteByTicket(@Param("ticketId") Long ticketId);
}
