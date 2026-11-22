package com.terp.erp.support.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {
    Optional<TicketCategory> findByIdAndDeletedFalse(Long id);

    List<TicketCategory> findByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);

    List<TicketCategory> findByIdInAndDeletedFalse(Collection<Long> ids);
}
