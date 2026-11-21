package com.terp.erp.attendance.leave;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface LeaveBalanceSyncRepository extends JpaRepository<LeaveBalanceSync, Long> {
    @Query("select s from LeaveBalanceSync s where s.companyId = :companyId and (:year is null or s.year = :year) "
            + "and (:kw = '' or lower(s.syncedBy) like :kw escape '!')")
    Page<LeaveBalanceSync> search(Long companyId, Integer year, String kw, Pageable pageable);

    Optional<LeaveBalanceSync> findFirstByCompanyIdAndYearOrderBySyncedAtDesc(Long companyId, int year);
}
