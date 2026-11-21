package com.terp.erp.attendance.biometric;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BiometricPunchRepository extends JpaRepository<BiometricPunch, Long> {
    @Query("select p.externalId from BiometricPunch p where p.deviceId = :deviceId and p.externalId in :ids")
    List<String> existingExternalIds(Long deviceId, Collection<String> ids);

    /** Punches of [from, to), oldest first. */
    @Query("select p from BiometricPunch p where p.companyId = :companyId and p.punchedAt >= :from "
            + "and p.punchedAt < :to order by p.punchedAt asc, p.id asc")
    List<BiometricPunch> between(Long companyId, LocalDateTime from, LocalDateTime to);

    @Query("select p from BiometricPunch p where p.companyId = :companyId and lower(p.employeeCode) = lower(:code) "
            + "and p.punchedAt >= :from and p.punchedAt < :to order by p.punchedAt asc, p.id asc")
    List<BiometricPunch> ofCode(Long companyId, String code, LocalDateTime from, LocalDateTime to);
}
