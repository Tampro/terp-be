package com.terp.erp.meeting.type;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.meeting.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface MeetingTypeRepository extends JpaRepository<MeetingType, Long> {
    /** keyword on name and description; the caller escapes with !. */
    @Query("select t from MeetingType t where t.companyId = :companyId and t.deleted = false "
            + "and (:status is null or t.status = :status) "
            + "and (:kw = '' or lower(t.name) like :kw escape '!' or lower(t.description) like :kw escape '!')")
    Page<MeetingType> search(Long companyId, EStatus status, String kw, Pageable pageable);

    Optional<MeetingType> findByIdAndDeletedFalse(Long id);

    List<MeetingType> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EStatus status);

    boolean existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(Long companyId, String name, Long id);
}
