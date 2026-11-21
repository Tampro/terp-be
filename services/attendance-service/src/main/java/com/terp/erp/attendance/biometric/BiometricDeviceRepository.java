package com.terp.erp.attendance.biometric;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BiometricDeviceRepository extends JpaRepository<BiometricDevice, Long> {
    Optional<BiometricDevice> findByApiKeyHashAndDeletedFalse(String apiKeyHash);

    Optional<BiometricDevice> findByIdAndDeletedFalse(Long id);

    Page<BiometricDevice> findByCompanyIdAndDeletedFalse(Long companyId, Pageable pageable);

    List<BiometricDevice> findByCompanyIdAndIdIn(Long companyId, Collection<Long> ids);

    @Query("select count(d) > 0 from BiometricDevice d where d.companyId = :companyId and d.deleted = false "
            + "and lower(d.serialNumber) = lower(:serial) and d.id <> :id")
    boolean serialTaken(Long companyId, String serial, Long id);
}
