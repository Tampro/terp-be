package com.terp.erp.support.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketFieldRepository extends JpaRepository<TicketField, Long> {
    Optional<TicketField> findByIdAndDeletedFalse(Long id);

    List<TicketField> findByCompanyIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long companyId);

    List<TicketField> findByCompanyIdAndActiveTrueAndDeletedFalseOrderBySortOrderAscIdAsc(Long companyId);
}
