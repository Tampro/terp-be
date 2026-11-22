package com.terp.erp.employee.training;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {
    /**
     * from / to on the start (to exclusive); keyword on the session or program name or the location; the caller
     * escapes with !.
     */
    String FILTERS = "from TrainingSession s, TrainingProgram p where p.id = s.programId "
            + "and s.companyId = :companyId and s.deleted = false "
            + "and (:programId is null or s.programId = :programId) "
            + "and (:locationType is null or s.locationType = :locationType) "
            + "and (:from is null or s.startAt >= :from) and (:to is null or s.startAt < :to) "
            + "and (:kw = '' or lower(s.name) like :kw escape '!' or lower(p.name) like :kw escape '!' "
            + "or lower(s.location) like :kw escape '!')";

    @Query(value = "select s " + FILTERS + " and (:status is null or s.status = :status)",
            countQuery = "select count(s) " + FILTERS + " and (:status is null or s.status = :status)")
    Page<TrainingSession> search(Long companyId, Long programId, TrainingSession.ELocationType locationType,
                                 LocalDateTime from, LocalDateTime to, TrainingSession.EStatus status, String kw,
                                 Pageable pageable);

    @Query("select s.status, count(s) " + FILTERS + " group by s.status")
    List<Object[]> countByStatus(Long companyId, Long programId, TrainingSession.ELocationType locationType,
                                 LocalDateTime from, LocalDateTime to, String kw);

    /** Calendar: sessions overlapping [from, to). */
    @Query("select s from TrainingSession s where s.companyId = :companyId and s.deleted = false "
            + "and s.startAt < :to and s.endAt >= :from and (:programId is null or s.programId = :programId) "
            + "and (:status is null or s.status = :status) order by s.startAt, s.id")
    List<TrainingSession> calendar(Long companyId, LocalDateTime from, LocalDateTime to, Long programId,
                                   TrainingSession.EStatus status);

    Optional<TrainingSession> findByIdAndDeletedFalse(Long id);

    boolean existsByTrainerIdAndDeletedFalse(Long trainerId);

    boolean existsByProgramIdAndDeletedFalse(Long programId);

    /** [programId, status, count] for the program cards. */
    @Query("select s.programId, s.status, count(s) from TrainingSession s where s.deleted = false "
            + "and s.programId in :programIds group by s.programId, s.status")
    List<Object[]> countByProgram(Collection<Long> programIds);
}
