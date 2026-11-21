package com.terp.erp.meeting.room;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.meeting.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface MeetingRoomRepository extends JpaRepository<MeetingRoom, Long> {
    /** keyword on name, description and location; the caller escapes with !. */
    String FILTERS = "from MeetingRoom r where r.companyId = :companyId and r.deleted = false "
            + "and (:type is null or r.type = :type) "
            + "and (:kw = '' or lower(r.name) like :kw escape '!' or lower(r.description) like :kw escape '!' "
            + "or lower(r.location) like :kw escape '!')";

    @Query(value = "select r " + FILTERS + " and (:status is null or r.status = :status)",
            countQuery = "select count(r) " + FILTERS + " and (:status is null or r.status = :status)")
    Page<MeetingRoom> search(Long companyId, MeetingRoom.EType type, EStatus status, String kw, Pageable pageable);

    @Query("select r.status, count(r) " + FILTERS + " group by r.status")
    List<Object[]> countByStatus(Long companyId, MeetingRoom.EType type, String kw);

    /** The summary cards: status and type of every room of the company. */
    @Query("select r.status, r.type, count(r) from MeetingRoom r where r.companyId = :companyId "
            + "and r.deleted = false group by r.status, r.type")
    List<Object[]> countByStatusAndType(Long companyId);

    Optional<MeetingRoom> findByIdAndDeletedFalse(Long id);

    List<MeetingRoom> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EStatus status);

    boolean existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(Long companyId, String name, Long id);
}
