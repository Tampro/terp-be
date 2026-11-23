package com.terp.erp.support.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketReplyRepository extends JpaRepository<TicketReply, Long> {
    List<TicketReply> findByTicketIdOrderByIdAsc(Long ticketId);

    /** [ticketId, first STAFF reply time] of these tickets. */
    @org.springframework.data.jpa.repository.Query("select r.ticketId, min(r.createdDate) from TicketReply r"
            + " where r.senderType = com.terp.erp.support.ticket.TicketReply.ESender.STAFF and r.ticketId in :ids"
            + " group by r.ticketId")
    List<Object[]> firstStaffReplies(@org.springframework.data.repository.query.Param("ids")
                                     java.util.Collection<Long> ticketIds);
}
