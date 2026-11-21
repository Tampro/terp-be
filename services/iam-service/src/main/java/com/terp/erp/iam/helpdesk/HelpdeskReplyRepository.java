package com.terp.erp.iam.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HelpdeskReplyRepository extends JpaRepository<HelpdeskReply, Long> {
    List<HelpdeskReply> findByTicketIdAndDeletedFalseOrderByIdAsc(Long ticketId);

    List<HelpdeskReply> findByTicketIdAndInternalFalseAndDeletedFalseOrderByIdAsc(Long ticketId);

    Optional<HelpdeskReply> findByIdAndTicketIdAndDeletedFalse(Long id, Long ticketId);
}
