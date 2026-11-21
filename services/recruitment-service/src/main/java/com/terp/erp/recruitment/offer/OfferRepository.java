package com.terp.erp.recruitment.offer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {
    /** Nightly expiry: open offers whose expiration date has passed become EXPIRED. Returns how many. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Offer o set o.status = com.terp.erp.recruitment.offer.Offer.EStatus.EXPIRED, "
            + "o.updatedDate = :now, o.updatedBy = 'system' where o.deleted = false and o.expirationDate < :today "
            + "and o.status in :open")
    int expireOverdue(java.time.LocalDate today, java.util.Collection<Offer.EStatus> open, java.time.Instant now);

    /** keyword matches the candidate's name or email, or the posting's title or code; the caller escapes with !. */
    String FILTERS = "from Offer o, com.terp.erp.recruitment.candidate.Candidate c, "
            + "com.terp.erp.recruitment.posting.JobPosting p "
            + "where c.id = o.candidateId and p.id = o.jobPostingId and o.companyId = :companyId and o.deleted = false "
            + "and (:candidateId is null or o.candidateId = :candidateId) "
            + "and (:kw = '' or lower(concat(c.firstName, ' ', c.lastName)) like :kw escape '!' "
            + "or lower(c.email) like :kw escape '!' or lower(p.title) like :kw escape '!' "
            + "or lower(p.jobCode) like :kw escape '!')";

    @Query(value = "select o " + FILTERS + " and (:status is null or o.status = :status)",
            countQuery = "select count(o) " + FILTERS + " and (:status is null or o.status = :status)")
    Page<Offer> search(Long companyId, Long candidateId, Offer.EStatus status, String kw, Pageable pageable);

    @Query("select o.status, count(o) " + FILTERS + " group by o.status")
    List<Object[]> countByStatus(Long companyId, Long candidateId, String kw);

    Optional<Offer> findByIdAndDeletedFalse(Long id);

    /** A deleted candidate takes their offers with them. */
    @Modifying
    @Query("update Offer o set o.deleted = true where o.candidateId = :candidateId and o.deleted = false")
    int deleteByCandidate(Long candidateId);
}
