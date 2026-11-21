package com.terp.erp.recruitment.location;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.recruitment.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface JobLocationRepository extends JpaRepository<JobLocation, Long> {
    String FILTERS = "from JobLocation l where l.companyId = :companyId and l.deleted = false "
            + "and (:remote is null or l.remote = :remote) "
            + "and (:kw = '' or lower(l.name) like :kw escape '!' or lower(l.address) like :kw escape '!' "
            + "or lower(l.city) like :kw escape '!' or lower(l.country) like :kw escape '!')";

    /** keyword matches name, address, city or country; remote = the Type filter (Remote / On-site). */
    @Query("select l " + FILTERS + " and (:status is null or l.status = :status)")
    Page<JobLocation> search(Long companyId, Boolean remote, EStatus status, String kw, Pageable pageable);

    @Query("select l.status, count(l) " + FILTERS + " group by l.status")
    List<Object[]> countByStatus(Long companyId, Boolean remote, String kw);

    List<JobLocation> findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Long companyId, EStatus status);

    Optional<JobLocation> findByIdAndDeletedFalse(Long id);

    @Query("select count(l) > 0 from JobLocation l where l.companyId = :companyId and l.deleted = false "
            + "and lower(l.name) = lower(:name) and l.id <> :id")
    boolean nameTaken(Long companyId, String name, Long id);
}
