package com.terp.erp.recruitment.offertemplate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.recruitment.common.EStatus;

import java.util.List;
import java.util.Optional;

public interface OfferTemplateRepository extends JpaRepository<OfferTemplate, Long> {
    String FILTERS = "from OfferTemplate t where t.companyId = :companyId and t.deleted = false "
            + "and (:kw = '' or lower(t.name) like :kw escape '!')";

    @Query("select t " + FILTERS + " and (:status is null or t.status = :status)")
    Page<OfferTemplate> search(Long companyId, EStatus status, String kw, Pageable pageable);

    @Query("select t.status, count(t) " + FILTERS + " group by t.status")
    List<Object[]> countByStatus(Long companyId, String kw);

    Optional<OfferTemplate> findByIdAndDeletedFalse(Long id);

    @Query("select count(t) > 0 from OfferTemplate t where t.companyId = :companyId and t.deleted = false "
            + "and lower(t.name) = lower(:name) and t.id <> :id")
    boolean nameTaken(Long companyId, String name, Long id);
}
