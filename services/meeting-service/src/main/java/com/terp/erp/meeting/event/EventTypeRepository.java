package com.terp.erp.meeting.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.meeting.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface EventTypeRepository extends JpaRepository<EventType, Long> {
    /** keyword on the name; the caller escapes with !. */
    @Query("select t from EventType t where t.companyId = :companyId and t.deleted = false "
            + "and (:status is null or t.status = :status) and (:kw = '' or lower(t.name) like :kw escape '!')")
    Page<EventType> search(Long companyId, EStatus status, String kw, Pageable pageable);

    Optional<EventType> findByIdAndDeletedFalse(Long id);

    List<EventType> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EStatus status);

    boolean existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(Long companyId, String name, Long id);
}
